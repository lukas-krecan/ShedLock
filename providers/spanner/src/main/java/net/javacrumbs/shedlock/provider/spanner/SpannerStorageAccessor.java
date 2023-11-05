package net.javacrumbs.shedlock.provider.spanner;

import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Key;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.Struct;
import com.google.cloud.spanner.TransactionContext;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class SpannerStorageAccessor extends AbstractStorageAccessor {

    private final String table;
    private final String name;
    private final String lockedBy;
    private final String lockUntil;
    private final String lockedAt;
    private final String hostname;
    private final DatabaseClient databaseClient;


    public SpannerStorageAccessor(SpannerLockProvider.Configuration configuration) {
        this.lockUntil = configuration.getTableConfiguration().getLockUntil();
        this.lockedAt = configuration.getTableConfiguration().getLockedAt();
        this.lockedBy = configuration.getTableConfiguration().getLockedBy();
        this.table = configuration.getTableConfiguration().getTableName();
        this.name = configuration.getTableConfiguration().getLockName();
        this.databaseClient = configuration.getDatabaseClient();
        this.hostname = configuration.getHostname();
    }

    @Override
    public boolean insertRecord(@NonNull LockConfiguration lockConfiguration) {
        return Boolean.TRUE.equals(databaseClient.readWriteTransaction().run(transaction ->
            findLock(transaction, lockConfiguration.getName())
                .map(lock -> false) // Lock already exists, so we return false.
                .orElseGet(() -> {
                    transaction.buffer(Mutation.newInsertBuilder(table)
                        .set(name).to(lockConfiguration.getName())
                        .set(lockUntil).to(toTimestamp(lockConfiguration.getLockAtMostUntil()))
                        .set(lockedAt).to(Timestamp.now())
                        .set(lockedBy).to(hostname)
                        .build());
                    return true;
                })
        ));
    }


    @Override
    public boolean updateRecord(@NonNull LockConfiguration lockConfiguration) {
        return Boolean.TRUE.equals(databaseClient.readWriteTransaction().run(transaction ->
            findLock(transaction, lockConfiguration.getName())
                .filter(lock -> lock.getLockedUntil().compareTo(Timestamp.now()) <= 0)
                .map(lock -> {
                    transaction.buffer(Mutation.newUpdateBuilder(table)
                        .set(name).to(lockConfiguration.getName())
                        .set(lockUntil).to(toTimestamp(lockConfiguration.getLockAtMostUntil()))
                        .set(lockedAt).to(Timestamp.now())
                        .set(lockedBy).to(hostname)
                        .build());
                    return true;
                }).orElse(false)
        ));
    }

    @Override
    public boolean extend(@NonNull LockConfiguration lockConfiguration) {
        return Boolean.TRUE.equals(databaseClient.readWriteTransaction().run(transaction ->
            findLock(transaction, lockConfiguration.getName())
                .filter(lock -> hostname.equals(lock.getLockedBy()))
                .filter(lock -> lock.getLockedUntil().compareTo(Timestamp.now()) > 0)
                .map(lock -> {
                    transaction.buffer(
                        Mutation.newUpdateBuilder(table)
                            .set(name).to(lockConfiguration.getName())
                            .set(lockUntil).to(toTimestamp(lockConfiguration.getLockAtMostUntil()))
                            .build());
                    return true;
                }).orElse(false)));
    }

    @Override
    public void unlock(@NonNull LockConfiguration lockConfiguration) {
        databaseClient.readWriteTransaction().run(transaction -> {
            findLock(transaction, lockConfiguration.getName())
                .filter(lock -> hostname.equals(lock.getLockedBy()))
                .ifPresent(lock ->
                    transaction.buffer(
                        Mutation.newUpdateBuilder(table)
                            .set(name).to(lockConfiguration.getName())
                            .set(lockUntil).to(toTimestamp(lockConfiguration.getUnlockTime()))
                            .build()));
            return null; // need a return to commit the transaction
        });
    }

    protected Optional<Lock> findLock(TransactionContext transaction, String lockName) {
        return Optional.ofNullable(transaction.readRow(
                table,
                Key.of(lockName),
                List.of(name, lockUntil, lockedBy, lockedAt)))
            .map(Lock::new);
    }

    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }

    protected class Lock {
        private final String lockName;
        private final String lockedBy;
        private final Timestamp lockedAt;
        private final Timestamp lockedUntil;

        protected Lock(@NonNull Struct row) {
            this.lockName = row.getString(name);
            this.lockedBy = row.getString(SpannerStorageAccessor.this.lockedBy);
            this.lockedAt = row.getTimestamp(SpannerStorageAccessor.this.lockedAt);
            this.lockedUntil = row.getTimestamp(lockUntil);
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
