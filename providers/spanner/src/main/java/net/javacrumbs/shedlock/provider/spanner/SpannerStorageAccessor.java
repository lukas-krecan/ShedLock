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

    public SpannerStorageAccessor(DatabaseClient databaseClient) {
        this.spannerClient = databaseClient;
        this.hostname = Utils.getHostname();
    }

    @Override
    public boolean insertRecord(@NonNull LockConfiguration lockConfiguration) {
        return Boolean.TRUE.equals(spannerClient.readWriteTransaction().run(transaction ->
            transactionallyFindLock(transaction, lockConfiguration)
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
            transactionallyFindLock(transaction, lockConfiguration)
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
            transactionallyFindLock(transaction, lockConfiguration)
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
            transactionallyFindLock(transaction, lockConfiguration)
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

    private Optional<SpannerLock> transactionallyFindLock(TransactionContext transaction, LockConfiguration lockConfiguration) {
        Struct row = transaction
            .readRow(
                TABLE,
                Key.of(lockConfiguration.getName()),
                List.of(LOCK_UNTIL, LOCKED_BY, LOCKED_AT));

        return Optional.ofNullable(row).map(SpannerLock::new);
    }

    protected Optional<SpannerLock> findLock(String lockName) {
        Struct row = spannerClient.singleUseReadOnlyTransaction()
            .readRow(
                TABLE,
                Key.of(lockName),
                List.of(LOCK_UNTIL, LOCKED_BY, LOCKED_AT));

        return Optional.ofNullable(row).map(SpannerLock::new);
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

        public String getLockName() {
            return lockName;
        }

        public String getLockedBy() {
            return lockedBy;
        }

        public Timestamp getLockedAt() {
            return lockedAt;
        }

        public Timestamp getLockedUntil() {
            return lockedUntil;
        }
    }

}
