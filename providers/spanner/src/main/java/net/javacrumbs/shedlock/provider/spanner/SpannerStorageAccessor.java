package net.javacrumbs.shedlock.provider.spanner;

import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.Key;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.Struct;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.Utils;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

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
        Boolean result = spannerClient.readWriteTransaction().run(transactionContext -> {
            Struct row = transactionContext.readRow(TABLE, Key.of(lockConfiguration.getName()), List.of(LOCK_UNTIL));

            if (row != null) {
                // Lock already exists
                return false;
            }

            Mutation insertMutation = Mutation.newInsertBuilder(TABLE)
                    .set(NAME)
                    .to(lockConfiguration.getName())
                    .set(LOCK_UNTIL)
                    .to(spannerTimestampOf(lockConfiguration.getLockAtMostUntil()))
                    .set(LOCKED_AT)
                    .to(Timestamp.now())
                    .set(LOCKED_BY)
                    .to(hostname)
                    .build();
            transactionContext.buffer(insertMutation);
            return true;
        });

        return Boolean.TRUE.equals(result);
    }

    @Override
    public boolean updateRecord(@NonNull LockConfiguration lockConfiguration) {
        Boolean result = spannerClient.readWriteTransaction().run(transactionContext -> {
            Struct row = transactionContext.readRow(TABLE, Key.of(lockConfiguration.getName()), List.of(LOCK_UNTIL));

            if (row == null) {
                return false;
            }

            Timestamp existingLockUntil = row.getTimestamp(LOCK_UNTIL);

            // Check if the lock can be updated based on the existing lock_until value
            if (existingLockUntil.compareTo(Timestamp.now()) <= 0) {

                Mutation updateMutation = Mutation.newUpdateBuilder(TABLE)
                        .set(NAME)
                        .to(lockConfiguration.getName())
                        .set(LOCK_UNTIL)
                        .to(spannerTimestampOf(lockConfiguration.getLockAtMostUntil()))
                        .set(LOCKED_AT)
                        .to(Timestamp.now())
                        .set(LOCKED_BY)
                        .to(hostname)
                        .build();
                transactionContext.buffer(updateMutation);
                return true;
            }
            return false; // Lock cannot be updated because it is still valid
        });

        return Boolean.TRUE.equals(result);
    }

    @Override
    public void unlock(@NonNull LockConfiguration lockConfiguration) {
        spannerClient.readWriteTransaction().run(transaction -> {
            Struct row = transaction.readRow(TABLE, Key.of(lockConfiguration.getName()), List.of(LOCKED_BY));

            // Proceed only if the row exists and lockedBy matches this instance
            if (row != null) {
                String lockedBy = row.getString(LOCKED_BY);
                if (hostname.equals(lockedBy)) {
                    Mutation mutation = Mutation.newUpdateBuilder(TABLE)
                            .set(NAME)
                            .to(lockConfiguration.getName())
                            .set(LOCK_UNTIL)
                            .to(spannerTimestampOf(lockConfiguration.getUnlockTime()))
                            .build();
                    transaction.buffer(mutation);
                }
            }
            return null;
        });
    }

    @Override
    public boolean extend(@NonNull LockConfiguration lockConfiguration) {
        Boolean result = spannerClient.readWriteTransaction().run(transaction -> {
            Struct row = transaction.readRow(
                    TABLE, Key.of(lockConfiguration.getName()), Arrays.asList(LOCK_UNTIL, LOCKED_BY));

            if (row == null) {
                return false;
            }

            String lockedBy = row.getString(LOCKED_BY);
            Timestamp lockUntil = row.getTimestamp(LOCK_UNTIL);

            // Extend lock only if held by the same instance and not expired
            if (hostname.equals(lockedBy) && lockUntil.compareTo(Timestamp.now()) > 0) {
                Mutation updateMutation = Mutation.newUpdateBuilder(TABLE)
                        .set(NAME)
                        .to(lockConfiguration.getName())
                        .set(LOCK_UNTIL)
                        .to(spannerTimestampOf(lockConfiguration.getLockAtMostUntil()))
                        .build();
                transaction.buffer(updateMutation);
                return true;
            }
            return false; // Not this instance's lock or expired.
        });
        return Boolean.TRUE.equals(result);
    }

    private Timestamp spannerTimestampOf(Instant instant) {
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }
}
