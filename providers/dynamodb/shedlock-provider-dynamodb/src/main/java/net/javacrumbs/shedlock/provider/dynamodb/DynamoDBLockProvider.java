/**
 * Copyright 2009-2018 the original author or authors.
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

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.UpdateItemOutcome;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ResourceInUseException;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.sun.org.apache.bcel.internal.generic.TABLESWITCH;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.Utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.Date;
import java.util.Optional;

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

    static final String DEFAULT_TABLE_NAME = "shedLock";

    private final String hostname;
    private final Table table;


    /**
     * Uses DynamoDB to coordinate locks stored in table {@value DEFAULT_TABLE_NAME}.
     *
     * @param dynamodb  DynamoDB to be used
     */
    public DynamoDBLockProvider(AmazonDynamoDB dynamodb) {
        this(dynamodb, DEFAULT_TABLE_NAME);
    }

    /**
     * Uses DynamoDB to coordinate locks
     *
     * @param dynamodb  DynamoDB to be used
     * @param tableName table to be used
     */
    public DynamoDBLockProvider(AmazonDynamoDB dynamodb, String tableName) {
        this.table = new DynamoDB(dynamodb).getTable(tableName);
        this.hostname = Utils.getHostname();
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        Instant now = now();
        String nowIso = toIsoString(now);
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
            UpdateItemOutcome updated = table.updateItem(request);
            assert lockUntilIso.equals(updated.getItem().getString(LOCK_UNTIL));
            return Optional.of(new DynamoDBLock(lockConfiguration));
        } catch (ConditionalCheckFailedException e) {
            // Condition failed. This means there was a lock with lockUntil > now.
            return Optional.empty();
        }
    }

    private String toIsoString(Instant temporal) {
        OffsetDateTime utc = temporal.atOffset(ZoneOffset.UTC);
        return DateTimeFormatter.ISO_DATE_TIME.format(utc);
    }

    private Instant now() {
        return Instant.now();
    }

    private final class DynamoDBLock implements SimpleLock {
        private final LockConfiguration lockConfiguration;

        private DynamoDBLock(LockConfiguration lockConfiguration) {
            this.lockConfiguration = lockConfiguration;
        }

        @Override
        public void unlock() {
            // Set lockUntil to now or lockAtLeastUntil whichever is later
            String unlockTimeIso = toIsoString(lockConfiguration.getUnlockTime());
            UpdateItemSpec request = new UpdateItemSpec()
                    .withPrimaryKey(ID, lockConfiguration.getName())
                    .withUpdateExpression(RELEASE_LOCK_QUERY)
                    .withValueMap(new ValueMap()
                            .withString(":lockUntil", unlockTimeIso)
                    )
                    .withReturnValues(ReturnValue.UPDATED_NEW);
            UpdateItemOutcome updated = table.updateItem(request);
            assert unlockTimeIso.equals(updated.getItem().getString(LOCK_UNTIL));
        }
    }

    /**
     * Creates a locking table with the given name.
     * <p>
     * This method does not check if a table with the given name exists already.
     *
     * @param dynamodb   DynamoDB to be used
     * @param tableName  table to be used
     * @param throughput AWS {@link ProvisionedThroughput throughput requirements} for the given lock setup
     * @return           a {@link TableDescription table description}
     *
     * @throws ResourceInUseException
     *         The operation conflicts with the resource's availability. You attempted to recreate an
     *         existing table.
     */
    public static TableDescription createLockTable(
            AmazonDynamoDB dynamodb,
            String tableName,
            ProvisionedThroughput throughput
    ) {

        CreateTableRequest request = new CreateTableRequest()
                .withTableName(tableName)
                .withKeySchema(new KeySchemaElement(ID, KeyType.HASH))
                .withAttributeDefinitions(new AttributeDefinition(ID, ScalarAttributeType.S))
                .withProvisionedThroughput(throughput);
        return dynamodb.createTable(request).getTableDescription();
    }
}
