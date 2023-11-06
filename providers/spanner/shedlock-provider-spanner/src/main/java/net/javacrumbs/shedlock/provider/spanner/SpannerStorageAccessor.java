package net.javacrumbs.shedlock.provider.spanner;

import static com.google.cloud.Timestamp.now;
import static com.google.cloud.spanner.Mutation.newInsertBuilder;
import static com.google.cloud.spanner.Mutation.newUpdateBuilder;

import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Key;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.Mutation.WriteBuilder;
import com.google.cloud.spanner.Struct;
import com.google.cloud.spanner.TransactionContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.spanner.SpannerLockProvider.TableConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.annotation.NonNull;

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
        TableConfiguration tableConfiguration = configuration.getTableConfiguration();
        this.lockUntil = tableConfiguration.getLockUntil();
        this.lockedAt = tableConfiguration.getLockedAt();
        this.lockedBy = tableConfiguration.getLockedBy();
        this.table = tableConfiguration.getTableName();
        this.name = tableConfiguration.getLockName();
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
        return Boolean.TRUE.equals(
                databaseClient.readWriteTransaction().run(tx -> findLock(tx, lockConfiguration.getName())
                        .map(lock -> false) // Lock already exists, so we return false.
                        .orElseGet(() -> {
                            tx.buffer(buildMutation(lockConfiguration, newInsertBuilder(table)));
                            return true;
                        })));
    }

    /**
     * Attempts to update an existing lock record in the Spanner table.
     *
     * @param lockConfiguration The lock configuration.
     * @return {@code true} if the lock was successfully updated, otherwise {@code false}.
     */
    @Override
    public boolean updateRecord(@NonNull LockConfiguration lockConfiguration) {
        return Boolean.TRUE.equals(databaseClient.readWriteTransaction().run(tx -> {
            return findLock(tx, lockConfiguration.getName())
                    .filter(lock -> lock.lockedUntil().compareTo(now()) <= 0)
                    .map(lock -> {
                        tx.buffer(buildMutation(lockConfiguration, newUpdateBuilder(table)));
                        return true;
                    })
                    .orElse(false);
        }));
    }

    private Mutation buildMutation(LockConfiguration lockConfiguration, WriteBuilder builder) {
        return builder.set(name)
                .to(lockConfiguration.getName())
                .set(lockUntil)
                .to(toTimestamp(lockConfiguration.getLockAtMostUntil()))
                .set(lockedAt)
                .to(now())
                .set(lockedBy)
                .to(hostname)
                .build();
    }

    /**
     * Extends the lock until time of an existing lock record if the current host holds the lock.
     *
     * @param lockConfiguration The lock configuration.
     * @return {@code true} if the lock was successfully extended, otherwise {@code false}.
     */
    @Override
    public boolean extend(@NonNull LockConfiguration lockConfiguration) {
        return Boolean.TRUE.equals(
                databaseClient.readWriteTransaction().run(tx -> findLock(tx, lockConfiguration.getName())
                        .filter(lock -> hostname.equals(lock.lockedBy()))
                        .filter(lock -> lock.lockedUntil().compareTo(now()) > 0)
                        .map(lock -> {
                            tx.buffer(newUpdateBuilder(table)
                                    .set(name)
                                    .to(lockConfiguration.getName())
                                    .set(lockUntil)
                                    .to(toTimestamp(lockConfiguration.getLockAtMostUntil()))
                                    .build());
                            return true;
                        })
                        .orElse(false)));
    }

    /**
     * Unlocks the lock by updating the lock record's lock until time to the unlock time.
     *
     * @param lockConfiguration The lock configuration to unlock.
     */
    @Override
    public void unlock(@NonNull LockConfiguration lockConfiguration) {
        databaseClient.readWriteTransaction().run(tx -> {
            findLock(tx, lockConfiguration.getName())
                    .filter(lock -> hostname.equals(lock.lockedBy()))
                    .ifPresent(lock -> tx.buffer(newUpdateBuilder(table)
                            .set(name)
                            .to(lockConfiguration.getName())
                            .set(lockUntil)
                            .to(toTimestamp(lockConfiguration.getUnlockTime()))
                            .build()));
            return null; // need a return to commit the transaction
        });
    }

    /**
     * Finds the lock in the Spanner table.
     *
     * @param tx The tx context to use for the read.
     * @param lockName    The name of the lock to find.
     * @return An {@code Optional<Lock>} containing the lock if found, otherwise empty.
     */
    Optional<Lock> findLock(TransactionContext tx, String lockName) {
        return Optional.ofNullable(tx.readRow(table, Key.of(lockName), List.of(name, lockUntil, lockedBy, lockedAt)))
                .map(this::newLock);
    }

    Lock newLock(@NonNull Struct row) {
        return new Lock(
                row.getString(name), row.getString(lockedBy), row.getTimestamp(lockedAt), row.getTimestamp(lockUntil));
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
    record Lock(String lockName, String lockedBy, Timestamp lockedAt, Timestamp lockedUntil) {}
}
