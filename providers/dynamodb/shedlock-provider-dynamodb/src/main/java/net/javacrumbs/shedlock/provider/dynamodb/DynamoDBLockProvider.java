/**
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.dynamodb;

import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.Utils;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import java.time.Instant;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.support.Utils.toIsoString;

/**
 * Distributed lock using DynamoDB.
 * Depends on <code>aws-java-sdk-dynamodb</code>.
 * <p>
 * It uses a table with the following structure:
 * <pre>
 * {
 *    "_id" : "lock name",
 *    "lockUntil" : ISODate("2017-01-07T16:52:04.071Z"),
 *    "lockedAt" : ISODate("2017-01-07T16:52:03.932Z"),
 *    "lockedBy" : "host name"
 * }
 * </pre>
 *
 * <code>lockedAt</code> and <code>lockedBy</code> are just for troubleshooting
 * and are not read by the code.
 *
 * <ol>
 * <li>
 * Attempts to insert a new lock record.
 * </li>
 * <li>
 * We will try to update lock record using <code>filter _id == :name AND lock_until &lt;= :now</code>.
 * </li>
 * <li>
 * If the update succeeded, we have the lock. If the update failed (condition check exception)
 * somebody else holds the lock.
 * </li>
 * <li>
 * When unlocking, <code>lock_until</code> is set to <i>now</i> or <i>lockAtLeastUntil</i> whichever is later.
 * </li>
 * </ol>
 */
public class DynamoDBLockProvider implements LockProvider {
    static final String LOCK_UNTIL = "lockUntil";
    static final String LOCKED_AT = "lockedAt";
    static final String LOCKED_BY = "lockedBy";
    static final String ID = "_id";

    private static final String OBTAIN_LOCK_QUERY =
            "set " + LOCK_UNTIL + " = :lockUntil, " + LOCKED_AT + " = :lockedAt, " + LOCKED_BY + " = :lockedBy";
    private static final String OBTAIN_LOCK_CONDITION =
            LOCK_UNTIL + " <= :lockedAt or attribute_not_exists(" + LOCK_UNTIL + ")";
    private static final String RELEASE_LOCK_QUERY =
            "set " + LOCK_UNTIL + " = :lockUntil";

    private final String hostname;
    private final Table table;

    /**
     * Uses DynamoDB to coordinate locks
     *
     * @param table existing DynamoDB table to be used
     */
    public DynamoDBLockProvider(@NonNull Table table) {
        this.table = requireNonNull(table, "table can not be null");
        this.hostname = Utils.getHostname();
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        String nowIso = toIsoString(now());
        String lockUntilIso = toIsoString(lockConfiguration.getLockAtMostUntil());

        UpdateItemSpec request = new UpdateItemSpec()
                .withPrimaryKey(ID, lockConfiguration.getName())
                .withUpdateExpression(OBTAIN_LOCK_QUERY)
                .withConditionExpression(OBTAIN_LOCK_CONDITION)
                .withValueMap(new ValueMap()
                        .withString(":lockUntil", lockUntilIso)
                        .withString(":lockedAt", nowIso)
                        .withString(":lockedBy", hostname)
                )
                .withReturnValues(ReturnValue.UPDATED_NEW);

        try {
            // There are three possible situations:
            // 1. The lock document does not exist yet - it is inserted - we have the lock
            // 2. The lock document exists and lockUtil <= now - it is updated - we have the lock
            // 3. The lock document exists and lockUtil > now - ConditionalCheckFailedException is thrown
            table.updateItem(request);
            return Optional.of(new DynamoDBLock(table, lockConfiguration));
        } catch (ConditionalCheckFailedException e) {
            // Condition failed. This means there was a lock with lockUntil > now.
            return Optional.empty();
        }
    }

    private Instant now() {
        return ClockProvider.now();
    }

    private static final class DynamoDBLock extends AbstractSimpleLock {
        private final Table table;

        private DynamoDBLock(Table table, LockConfiguration lockConfiguration) {
            super(lockConfiguration);
            this.table = table;
        }

        @Override
        public void doUnlock() {
            // Set lockUntil to now or lockAtLeastUntil whichever is later
            String unlockTimeIso = toIsoString(lockConfiguration.getUnlockTime());
            UpdateItemSpec request = new UpdateItemSpec()
                    .withPrimaryKey(ID, lockConfiguration.getName())
                    .withUpdateExpression(RELEASE_LOCK_QUERY)
                    .withValueMap(new ValueMap()
                            .withString(":lockUntil", unlockTimeIso)
                    )
                    .withReturnValues(ReturnValue.UPDATED_NEW);
            table.updateItem(request);
        }
    }
}
