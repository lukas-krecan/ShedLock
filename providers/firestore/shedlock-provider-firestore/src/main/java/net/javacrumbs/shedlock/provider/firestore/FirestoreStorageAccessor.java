package net.javacrumbs.shedlock.provider.firestore;

import static java.util.Objects.requireNonNull;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.Utils;
import net.javacrumbs.shedlock.support.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FirestoreStorageAccessor extends AbstractStorageAccessor {
    private static final Logger log = LoggerFactory.getLogger(FirestoreStorageAccessor.class);

    private final Firestore firestore;
    private final String hostname;
    private final String collectionName;
    private final FirestoreLockProvider.FieldNames fieldNames;

    public FirestoreStorageAccessor(FirestoreLockProvider.Configuration configuration) {
        requireNonNull(configuration);
        this.firestore = configuration.getFirestore();
        this.hostname = Utils.getHostname();
        this.collectionName = configuration.getCollectionName();
        this.fieldNames = configuration.getFieldNames();
    }

    @Override
    public boolean insertRecord(LockConfiguration config) {
        return insert(config.getName(), config.getLockAtMostUntil());
    }

    @Override
    public boolean updateRecord(LockConfiguration config) {
        return updateExisting(config.getName(), config.getLockAtMostUntil());
    }

    @Override
    public void unlock(LockConfiguration config) {
        updateOwn(config.getName(), config.getUnlockTime());
    }

    @Override
    public boolean extend(LockConfiguration config) {
        return updateOwn(config.getName(), config.getLockAtMostUntil());
    }

    private boolean insert(String name, Instant until) {
        // Firestore overrides the data if the document already exists, so we need to check if it exists first
        ApiFuture<Boolean> future = firestore.runTransaction(transaction -> {
            DocumentReference lockDocumentRef =
                    firestore.collection(this.collectionName).document(name);
            var snapshot = transaction.get(lockDocumentRef).get();
            if (snapshot.exists()) {
                return false;
            }
            Map<String, Object> data = new HashMap<>();
            data.put(fieldNames.lockUntil(), fromInstant(until));
            data.put(fieldNames.lockedAt(), fromInstant(ClockProvider.now()));
            data.put(fieldNames.lockedBy(), this.hostname);
            transaction.set(lockDocumentRef, data);
            return true;
        });
        try {
            return future.get();
        } catch (Exception e) {
            log.debug("Unable to perform a transactional unit of work: {}", e.getMessage());
            return false;
        }
    }

    private boolean updateExisting(String name, Instant until) {
        DocumentReference lockDocumentRef =
                firestore.collection(this.collectionName).document(name);

        if (Boolean.TRUE.equals(isLockExpired(lockDocumentRef))) {
            WriteBatch writeBatch = firestore.batch();
            writeBatch.update(lockDocumentRef, this.fieldNames.lockUntil(), fromInstant(until));
            writeBatch.update(lockDocumentRef, this.fieldNames.lockedAt(), fromInstant(ClockProvider.now()));
            writeBatch.update(lockDocumentRef, this.fieldNames.lockedBy(), this.hostname);
            try {
                writeBatch.commit().get();
            } catch (Exception e) {
                log.debug("Unable to perform a transactional unit of work: {}", e.getMessage());
                return false;
            }
            return true;
        }
        return false;
    }

    private Boolean isLockExpired(DocumentReference lockDocumentRef) {
        try {
            var now = ClockProvider.now();
            var lockUntilTs = nullableTimestamp(lockDocumentRef, this.fieldNames.lockUntil());
            return lockUntilTs != null && (lockUntilTs.isBefore(now) || lockUntilTs.equals(now));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean updateOwn(String name, Instant until) {
        DocumentReference lockDocumentRef =
                firestore.collection(this.collectionName).document(name);

        if (Boolean.TRUE.equals(isLockStillValidAndOwned(lockDocumentRef))) {
            WriteBatch writeBatch = firestore.batch();
            writeBatch.update(lockDocumentRef, this.fieldNames.lockUntil(), fromInstant(until));
            try {
                writeBatch.commit().get();
            } catch (Exception e) {
                log.debug("Unable to perform a transactional unit of work: {}", e.getMessage());
                return false;
            }
            return true;
        }
        return false;
    }

    private Boolean isLockStillValidAndOwned(DocumentReference lockDocumentRef) {
        try {
            var isOwner = this.hostname.equals(nullableString(lockDocumentRef, this.fieldNames.lockedBy()));
            var now = ClockProvider.now();
            var lockUntilTs = nullableTimestamp(lockDocumentRef, this.fieldNames.lockUntil());
            return isOwner && (lockUntilTs != null && (lockUntilTs.isAfter(now) || lockUntilTs.equals(now)));
        } catch (Exception e) {
            return null;
        }
    }

    public Optional<Lock> findLock(String name) throws ExecutionException, InterruptedException {
        DocumentReference lockDocumentRef =
                firestore.collection(this.collectionName).document(name);
        var snapshot = lockDocumentRef.get().get();
        if (snapshot.exists()) {
            return Optional.of(new Lock(
                    snapshot.getId(),
                    nullableTimestamp(lockDocumentRef, this.fieldNames.lockedAt()),
                    nullableTimestamp(lockDocumentRef, this.fieldNames.lockUntil()),
                    nullableString(lockDocumentRef, this.fieldNames.lockedBy())));
        } else {
            return Optional.empty();
        }
    }

    @Nullable
    private static String nullableString(DocumentReference documentReference, String property)
            throws ExecutionException, InterruptedException {
        return documentReference.get().get().contains(property)
                ? (requireNonNull(documentReference.get().get().get(property)).toString())
                : null;
    }

    @Nullable
    private static Instant nullableTimestamp(DocumentReference documentReference, String property)
            throws ExecutionException, InterruptedException {
        return documentReference.get().get().contains(property)
                ? toInstant((Timestamp) documentReference.get().get().get(property))
                : null;
    }

    private static Timestamp fromInstant(Instant instant) {
        return Timestamp.of(java.sql.Timestamp.from(requireNonNull(instant)));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return requireNonNull(timestamp).toSqlTimestamp().toInstant();
    }

    public record Lock(
            String name, @Nullable Instant lockedAt, @Nullable Instant lockedUntil, @Nullable String lockedBy) {}
}
