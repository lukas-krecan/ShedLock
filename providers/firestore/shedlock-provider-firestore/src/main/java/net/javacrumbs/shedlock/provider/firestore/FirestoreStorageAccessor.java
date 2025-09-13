package net.javacrumbs.shedlock.provider.firestore;

import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.core.ClockProvider.now;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Transaction;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class FirestoreStorageAccessor extends AbstractStorageAccessor {
    private static final Logger log = LoggerFactory.getLogger(FirestoreStorageAccessor.class);

    private final Firestore firestore;
    private final String hostname;
    private final String collectionName;
    private final FirestoreLockProvider.FieldNames fieldNames;

    FirestoreStorageAccessor(FirestoreLockProvider.Configuration configuration) {
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
        try {
            DocumentReference docRef = getDocument(name);

            // Try to create the document if it doesn't exist
            Map<String, Object> lockData = getLockData(until);
            log.debug("Inserting lock {}", docRef);

            return runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(docRef).get();
                if (!snapshot.exists()) {
                    transaction.set(docRef, lockData);
                    log.debug("Lock inserted {}", docRef);
                    return true;
                }
                return false;
            });
        } catch (InterruptedException | ExecutionException e) {
            log.debug("Error inserting lock record", e);
            return false;
        }
    }

    private boolean updateExisting(String name, Instant until) {
        try {
            DocumentReference docRef = getDocument(name);

            return runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(docRef).get();
                if (snapshot.exists()) {
                    Timestamp lockUntilTs = snapshot.getTimestamp(fieldNames.lockUntil());
                    if (lockUntilTs != null && toInstant(lockUntilTs).isBefore(now())) {
                        Map<String, Object> updates = getLockData(until);
                        transaction.update(docRef, updates);
                        return true;
                    }
                }
                return false;
            });
        } catch (InterruptedException | ExecutionException e) {
            log.debug("Error updating lock record", e);
            return false;
        }
    }

    private Map<String, Object> getLockData(Instant until) {
        return Map.of(
                fieldNames.lockUntil(), fromInstant(until),
                fieldNames.lockedAt(), fromInstant(now()),
                fieldNames.lockedBy(), hostname);
    }

    private DocumentReference getDocument(String name) {
        return firestore.collection(collectionName).document(name);
    }

    private boolean updateOwn(String name, Instant until) {
        try {
            DocumentReference docRef = getDocument(name);

            return runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(docRef).get();
                if (snapshot.exists() && hostname.equals(snapshot.getString(fieldNames.lockedBy()))) {

                    Timestamp lockUntilTs = snapshot.getTimestamp(fieldNames.lockUntil());
                    if (lockUntilTs != null) {
                        Instant lockUntil = toInstant(lockUntilTs);
                        Instant now = now();
                        if (lockUntil.isAfter(now) || lockUntil.equals(now)) {
                            Map<String, Object> updates = Map.of(fieldNames.lockUntil(), fromInstant(until));
                            transaction.update(docRef, updates);
                            return true;
                        }
                    }
                }
                return false;
            });
        } catch (InterruptedException | ExecutionException e) {
            log.debug("Error updating own lock record", e);
            return false;
        }
    }

    Optional<Lock> findLock(String name) {
        try {
            DocumentReference docRef = getDocument(name);
            DocumentSnapshot snapshot = docRef.get().get();

            if (snapshot.exists()) {
                return Optional.of(new Lock(
                        snapshot.getId(),
                        toInstant(snapshot.getTimestamp(fieldNames.lockedAt())),
                        toInstant(snapshot.getTimestamp(fieldNames.lockUntil())),
                        snapshot.getString(fieldNames.lockedBy())));
            }
            return Optional.empty();
        } catch (InterruptedException | ExecutionException e) {
            log.debug("Error finding lock", e);
            return Optional.empty();
        }
    }

    private <T> T runTransaction(final Transaction.Function<T> updateFunction)
            throws ExecutionException, InterruptedException {
        return firestore.runTransaction(updateFunction).get();
    }

    private static Timestamp fromInstant(Instant instant) {
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }

    private static Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

    record Lock(String name, Instant lockedAt, Instant lockedUntil, String lockedBy) {}
}
