package net.javacrumbs.shedlock.provider.spanner;

import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Key;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.Struct;
import com.google.cloud.spanner.TransactionContext;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.Utils;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class SpannerStorageAccessor extends AbstractStorageAccessor {

    private final String LOCK_UNTIL = "lock_until";
    private final String LOCKED_AT = "locked_at";
    private final String LOCKED_BY = "locked_by";
    private final String TABLE = "shedlock";
    private final String NAME = "name";
    private final String hostname;
    private final DatabaseClient spannerClient;

    private final Logger LOGGER = LoggerFactory.getLogger(SpannerStorageAccessor.class);

    public SpannerStorageAccessor(DatabaseClient databaseClient) {
        this.spannerClient = databaseClient;
        this.hostname = Utils.getHostname();
    }

    @Override
    public boolean insertRecord(@NonNull LockConfiguration lockConfiguration) {
        return Boolean.TRUE.equals(spannerClient.readWriteTransaction().run(transaction ->
            findLock(transaction, lockConfiguration.getName())
                .map(lock -> false) // Lock already exists, so we return false.
                .orElseGet(() -> {
                    transaction.buffer(Mutation.newInsertBuilder(TABLE)
                        .set(NAME).to(lockConfiguration.getName())
                        .set(LOCK_UNTIL).to(toTimestamp(lockConfiguration.getLockAtMostUntil()))
                        .set(LOCKED_AT).to(Timestamp.now())
                        .set(LOCKED_BY).to(hostname)
                        .build());
                    return true;
                })
        ));
    }


    @Override
    public boolean updateRecord(@NonNull LockConfiguration lockConfiguration) {
        return Boolean.TRUE.equals(spannerClient.readWriteTransaction().run(transaction ->
            findLock(transaction, lockConfiguration.getName())
                .filter(lock -> lock.getLockedUntil().compareTo(Timestamp.now()) <= 0)
                .map(lock -> {
                    transaction.buffer(Mutation.newUpdateBuilder(TABLE)
                        .set(NAME).to(lockConfiguration.getName())
                        .set(LOCK_UNTIL).to(toTimestamp(lockConfiguration.getLockAtMostUntil()))
                        .set(LOCKED_AT).to(Timestamp.now())
                        .set(LOCKED_BY).to(hostname)
                        .build());
                    return true;
                }).orElse(false)
        ));
    }

    @Override
    public boolean extend(@NonNull LockConfiguration lockConfiguration) {
        return Boolean.TRUE.equals(spannerClient.readWriteTransaction().run(transaction ->
            findLock(transaction, lockConfiguration.getName())
                .filter(lock -> hostname.equals(lock.getLockedBy()))
                .filter(lock -> lock.getLockedUntil().compareTo(Timestamp.now()) > 0)
                .map(lock -> {
                    transaction.buffer(
                        Mutation.newUpdateBuilder(TABLE)
                            .set(NAME).to(lockConfiguration.getName())
                            .set(LOCK_UNTIL).to(toTimestamp(lockConfiguration.getLockAtMostUntil()))
                            .build());
                    return true;
                }).orElse(false)));
    }

    @Override
    public void unlock(@NonNull LockConfiguration lockConfiguration) {
        spannerClient.readWriteTransaction().run(transaction -> {
            findLock(transaction, lockConfiguration.getName())
                .filter(lock -> hostname.equals(lock.getLockedBy()))
                .ifPresent(lock ->
                    transaction.buffer(
                        Mutation.newUpdateBuilder(TABLE)
                            .set(NAME).to(lockConfiguration.getName())
                            .set(LOCK_UNTIL).to(toTimestamp(lockConfiguration.getUnlockTime()))
                            .build()));
            return null;
        });
    }

    protected Optional<SpannerLock> findLock(TransactionContext transaction, String lockName) {
        return Optional.ofNullable(transaction.readRow(
                TABLE,
                Key.of(lockName),
                List.of(NAME, LOCK_UNTIL, LOCKED_BY, LOCKED_AT)))
            .map(SpannerLock::new);
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }

    protected class SpannerLock {
        private final String lockName;
        private final String lockedBy;
        private final Timestamp lockedAt;
        private final Timestamp lockedUntil;

        protected SpannerLock(@NonNull Struct row) {
            this.lockName = row.getString(NAME);
            this.lockedBy = row.getString(LOCKED_BY);
            this.lockedAt = row.getTimestamp(LOCKED_AT);
            this.lockedUntil = row.getTimestamp(LOCK_UNTIL);
        }

        protected String getLockName() {
            return lockName;
        }

        protected String getLockedBy() {
            return lockedBy;
        }

        protected Timestamp getLockedAt() {
            return lockedAt;
        }

        protected Timestamp getLockedUntil() {
            return lockedUntil;
        }
    }

}
