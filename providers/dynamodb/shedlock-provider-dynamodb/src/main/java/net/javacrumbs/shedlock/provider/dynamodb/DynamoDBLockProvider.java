/**
 * Copyright 2009-2020 the original author or authors.
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

import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.Utils;
import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static net.javacrumbs.shedlock.support.Utils.toIsoString;

/**
 * Distributed lock using DynamoDB.
 * Depends on <code>software.amazon.awssdk:dynamodb</code>.
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
    private final DynamoDbClient ddbClient;
    private final String tableName;

    /**
     * Uses DynamoDB to coordinate locks
     *
     * @param ddbClient v2 of DynamoDB client
     * @param tableName the lock table name
     */
    public DynamoDBLockProvider(@NotNull DynamoDbClient ddbClient, String tableName) {
        this.ddbClient = ddbClient;
        this.hostname = Utils.getHostname();
        this.tableName = tableName;
    }

    @Override
    @NotNull
    public Optional<SimpleLock> lock(@NotNull LockConfiguration lockConfiguration) {
        String nowIso = toIsoString(now());
        String lockUntilIso = toIsoString(lockConfiguration.getLockAtMostUntil());

        Map<String, AttributeValue> key = Collections.singletonMap(ID, AttributeValue.builder()
                .s(lockConfiguration.getName())
                .build());

        Map<String, AttributeValue> attributeUpdates = new HashMap<>(3);
        attributeUpdates.put(":lockUntil", AttributeValue.builder()
                        .s(lockUntilIso)
                        .build());
        attributeUpdates.put(":lockedAt", AttributeValue.builder()
                        .s(nowIso)
                        .build());
        attributeUpdates.put(":lockedBy", AttributeValue.builder()
                        .s(hostname)
                        .build());

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName("jobs")
                .key(key)
                .updateExpression(OBTAIN_LOCK_QUERY)
                .conditionExpression(OBTAIN_LOCK_CONDITION)
                .expressionAttributeValues(attributeUpdates)
                .returnValues(ReturnValue.UPDATED_NEW)
                .build();

        try {
            // There are three possible situations:
            // 1. The lock document does not exist yet - it is inserted - we have the lock
            // 2. The lock document exists and lockUtil <= now - it is updated - we have the lock
            // 3. The lock document exists and lockUtil > now - ConditionalCheckFailedException is thrown
            UpdateItemResponse response = ddbClient.updateItem(request);
            assert lockUntilIso.equals(response.getValueForField(LOCK_UNTIL, String.class));
            return Optional.of(new DynamoDBLock(lockConfiguration));
        } catch (ConditionalCheckFailedException e) {
            // Condition failed. This means there was a lock with lockUntil > now.
            return Optional.empty();
        }
    }

    private Instant now() {
        return ClockProvider.now();
    }

    private final class DynamoDBLock extends AbstractSimpleLock {
        private DynamoDBLock(LockConfiguration lockConfiguration) {
            super(lockConfiguration);
        }

        @Override
        public void doUnlock() {
            // Set lockUntil to now or lockAtLeastUntil whichever is later
            String unlockTimeIso = toIsoString(lockConfiguration.getUnlockTime());

            Map<String, AttributeValue> key = Collections.singletonMap(ID, AttributeValue.builder()
                    .s(lockConfiguration.getName())
                    .build());

            Map<String, AttributeValue> attributeUpdates = Collections.singletonMap(":lockUntil", AttributeValue.builder()
                            .s(unlockTimeIso)
                            .build());


            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .updateExpression(RELEASE_LOCK_QUERY)
                    .expressionAttributeValues(attributeUpdates)
                    .returnValues(ReturnValue.UPDATED_NEW)
                    .build();

            UpdateItemResponse response = ddbClient.updateItem(request);
            assert unlockTimeIso.equals(response.getValueForField(LOCK_UNTIL, String.class));
        }
    }
}
