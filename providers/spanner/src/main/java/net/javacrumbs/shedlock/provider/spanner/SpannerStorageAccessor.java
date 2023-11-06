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

/**
 * Accessor for managing lock records within a Google Spanner database.
 * This class is responsible for inserting, updating, extending, and unlocking
 * lock records using Spanner's transactions.
 */
public class SpannerStorageAccessor extends AbstractStorageAccessor {

    private final String table;
    private final String name;
    private final String lockedBy;
    private final String lockUntil;
    private final String lockedAt;
    private final String hostname;
    private final DatabaseClient databaseClient;

    /**
     * Constructs a {@code SpannerStorageAccessor} using the specified configuration.
     *
     * @param configuration The lock provider configuration.
     */
    public SpannerStorageAccessor(SpannerLockProvider.Configuration configuration) {
        this.lockUntil = configuration.getTableConfiguration().getLockUntil();
        this.lockedAt = configuration.getTableConfiguration().getLockedAt();
        this.lockedBy = configuration.getTableConfiguration().getLockedBy();
        this.table = configuration.getTableConfiguration().getTableName();
        this.name = configuration.getTableConfiguration().getLockName();
        this.databaseClient = configuration.getDatabaseClient();
        this.hostname = configuration.getHostname();
    }

    /**
     * Attempts to insert a lock record into the Spanner table.
     *
     * @param lockConfiguration The lock configuration.
     * @return {@code true} if the lock was successfully inserted, otherwise {@code false}.
     */
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


    /**
     * Attempts to update an existing lock record in the Spanner table.
     *
     * @param lockConfiguration The lock configuration.
     * @return {@code true} if the lock was successfully updated, otherwise {@code false}.
     */
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

    /**
     * Extends the lock until time of an existing lock record if the current host holds the lock.
     *
     * @param lockConfiguration The lock configuration.
     * @return {@code true} if the lock was successfully extended, otherwise {@code false}.
     */
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

    /**
     * Unlocks the lock by updating the lock record's lock until time to the unlock time.
     *
     * @param lockConfiguration The lock configuration to unlock.
     */
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

    /**
     * Finds the lock in the Spanner table.
     *
     * @param transaction The transaction context to use for the read.
     * @param lockName    The name of the lock to find.
     * @return An {@code Optional<Lock>} containing the lock if found, otherwise empty.
     */
    protected Optional<Lock> findLock(TransactionContext transaction, String lockName) {
        return Optional.ofNullable(transaction.readRow(
                table,
                Key.of(lockName),
                List.of(name, lockUntil, lockedBy, lockedAt)))
            .map(Lock::new);
    }

    /**
     * Converts {@code Instant} to {@code Timestamp}.
     *
     * @param instant The instant to convert.
     * @return The corresponding {@code Timestamp}.
     */
    private Timestamp toTimestamp(Instant instant) {
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }

    /**
     * Inner class representing a lock record from Spanner.
     */
    protected class Lock {
        private final String lockName;
        private final String lockedBy;
        private final Timestamp lockedAt;
        private final Timestamp lockedUntil;

        /**
         * Constructs a {@code Lock} instance based on the Spanner row structure.
         *
         * @param row The Spanner row containing lock information.
         */
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
