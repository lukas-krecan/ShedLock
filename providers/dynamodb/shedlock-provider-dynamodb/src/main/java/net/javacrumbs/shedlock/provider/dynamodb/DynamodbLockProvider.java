/**
 * Copyright 2009-2018 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.document.DeleteItemOutcome;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.UpdateItemOutcome;
import com.amazonaws.services.dynamodbv2.document.spec.DeleteItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.util.TableUtils;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;


/**
 * Distributed lock using Dynamo Db. Requires AWS Java SDK V 1.x
 * <p>
 * It uses a collection that contains documents like this:
 * <pre>
 * {
 *    "lockName" : "lock name",
 *    "lockUntil" : ISODate("2017-01-07T16:52:04.071Z"),
 *    "lockedAt" : ISODate("2017-01-07T16:52:03.932Z"),
 *    "lockedBy" : "node name"
 * }
 * </pre>
 * <p>
 * lockedAt and lockedBy are just for troubleshooting and are not read by the code
 * <p>
 * <ol>
 * <li>
 * Attempts to insert a new lock record.
 * </li>
 * <li>
 * We will try to update lock record using filter lockName == name AND lock_until &lt;= now
 * </li>
 * <li>
 * If the update succeeded (1 updated document), we have the lock. If the update failed (0 updated documents) somebody else holds the lock
 * </li>
 * <li>
 * When unlocking, lock_until is set to now.
 * </li>
 * </ol>
 */
public class DynamodbLockProvider implements LockProvider {
    private static final String UPDATE_EXPRESSION = buildUpdateExpression();
    private static final String UPSERT_CONDITION = buildUpsertCondition();
    private static final String UNLOCK_CONDITION = buildUnlockCondition();
    private static final String LOCK_CLEANUP_CONDITION = buildCleanupCondition();

    private static final String NOW = "now";

    static final String LOCK_UNTIL = "lockUntil";
    static final String LOCKED_AT = "lockedAt";
    static final String LOCKED_BY = "lockedBy";
    static final String NAME = "lockName";
    static final String NODE_NAME = getHostname();

    private final AmazonDynamoDB amazonDynamoDB;
    private final String tableName;
    private final Table table;


    private static String buildUpsertCondition() {
        return String.format("attribute_not_exists(%s) OR %s <= :%s", NAME, LOCK_UNTIL, NOW);
    }

    private static String buildCleanupCondition() {
        return String.format("attribute_exists(%s) AND %s <= :%s", NAME, LOCK_UNTIL, NOW);
    }

    private static String buildUnlockCondition() {
        return String.format("attribute_exists(%s)", NAME);
    }

    private static String buildUpdateExpression() {
        return String.format("set %s=:%s, %s=:%s, %s=:%s", LOCKED_BY, LOCKED_BY, LOCK_UNTIL, LOCK_UNTIL, LOCKED_AT, LOCKED_AT);
    }

    private static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }


    /**
     * Uses Amazon Dynamo Db to coordinate locks
     *
     * @param amazonDynamoDB Amazon Dynamo DB Database.
     *                       Sample code :
     *                       <pre>
     *                       AmazonDynamoDB amazonDynamoDB = AmazonDynamoDBClientBuilder.standard()
     *                                      .withEndpointConfiguration(
     *                                           new AwsClientBuilder.EndpointConfiguration(AMAZON_DYNAMODB_URL, REGION_US_WEST_2))
     *                                      .build();
     *  </pre>
     */
    public DynamodbLockProvider(AmazonDynamoDB amazonDynamoDB) {
        this.amazonDynamoDB = amazonDynamoDB;
        this.tableName = "shedLock";
        new Builder().withAmazonDynamodb(amazonDynamoDB)
                .createTable(tableName,
                        new ThroughputBuilder(Duration.of(1, ChronoUnit.MINUTES), 1)
                                .getProvisionedThroughput())
                .build();
        this.table = new DynamoDB(amazonDynamoDB).getTable(tableName);
    }

    /**
     * Uses Amazon Dynamo Db to coordinate locks
     *
     * @param amazonDynamoDB Amazon Dynamo DB Database.
     * @param tableName      Dynamo db table name.
     *                       The table should be created by now. If this is not the case
     *                       Consider using DynamodbLockProvider.Builder
     *
     *                       Sample code :
     *                       <pre>
     *                       AmazonDynamoDB amazonDynamoDB = AmazonDynamoDBClientBuilder.standard()
     *                                      .withEndpointConfiguration(
     *                                            new AwsClientBuilder.EndpointConfiguration(AMAZON_DYNAMODB_URL, REGION_US_WEST_2))
     *                                       .build();
     * </pre>
     */
    public DynamodbLockProvider(AmazonDynamoDB amazonDynamoDB, String tableName) {
        this.amazonDynamoDB = amazonDynamoDB;
        this.tableName = tableName;
        this.table = new DynamoDB(amazonDynamoDB).getTable(tableName);
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {

        boolean lockCreated = createLockRecord(lockConfiguration);
        if (lockCreated) {
            return Optional.of(new DynamodbLock(lockConfiguration));
        } else {
            return Optional.empty();
        }
    }

    public boolean createLockRecord(LockConfiguration lockConfiguration) {

        //Clean up expired lock
        DeleteItemSpec deleteItemSpec = new DeleteItemSpec()
                .withPrimaryKey(NAME, lockConfiguration.getName())
                .withConditionExpression(LOCK_CLEANUP_CONDITION)
                .withValueMap(
                        new ValueMap()
                                .withLong(":" + NOW, Instant.now().toEpochMilli()))
                .withReturnValues(ReturnValue.ALL_OLD);
        try {
            table.deleteItem(deleteItemSpec);
        } catch (ConditionalCheckFailedException e) {
            //Nothing to clean up;
        }

        UpdateItemSpec spec = new UpdateItemSpec()
                .withPrimaryKey(NAME, lockConfiguration.getName())
                .withUpdateExpression(UPDATE_EXPRESSION)
                .withConditionExpression(UPSERT_CONDITION)
                .withValueMap(
                        new ValueMap()
                                .withString(":" + LOCKED_BY, NODE_NAME)
                                .withLong(":" + LOCK_UNTIL, lockConfiguration.getLockAtMostUntil().toEpochMilli())
                                .withLong(":" + LOCKED_AT, Instant.now().toEpochMilli())
                                .withLong(":" + NOW, Instant.now().toEpochMilli()))
                .withReturnValues(ReturnValue.ALL_OLD);

        try {
            UpdateItemOutcome updateItemOutcome = table.updateItem(spec);
            return (updateItemOutcome.getItem() == null);
        } catch(ConditionalCheckFailedException ce) {
            return false;
        }
    }

    class DynamodbLock implements SimpleLock {

        private LockConfiguration lockConfiguration;

        public DynamodbLock(LockConfiguration lockConfiguration) {
            this.lockConfiguration = lockConfiguration;
        }

        @Override
        public void unlock() {
            Instant keepLockFor = lockConfiguration.getLockAtLeastUntil();
            if (keepLockFor.isBefore(Instant.now())) {
                DeleteItemSpec spec = new DeleteItemSpec()
                        .withPrimaryKey(NAME, lockConfiguration.getName())
                        .withReturnValues(ReturnValue.ALL_OLD);
                table.deleteItem(spec);
            } else {
                UpdateItemSpec spec = new UpdateItemSpec()
                        .withPrimaryKey(NAME, lockConfiguration.getName())
                        .withUpdateExpression(UPDATE_EXPRESSION)
                        .withConditionExpression(UNLOCK_CONDITION)
                        .withValueMap(
                                new ValueMap()
                                        .withString(":" + LOCKED_BY, NODE_NAME)
                                        .withLong(":" + LOCK_UNTIL, lockConfiguration.getLockAtLeastUntil().toEpochMilli())
                                        .withLong(":" + LOCKED_AT, Instant.now().toEpochMilli()))
                        .withReturnValues(ReturnValue.ALL_NEW);

                table.updateItem(spec);
            }
        }
    }


    private class ThroughputBuilder {
        private Duration minLockTime;
        private long concurrentProcessCount;

        public ThroughputBuilder(Duration minLockTime, long concurrentProcessCount) {
            this.minLockTime = minLockTime;
            this.concurrentProcessCount = concurrentProcessCount;
        }

        public ProvisionedThroughput getProvisionedThroughput() {
            Double writeQueryPerSecond = Math.ceil(1 / (minLockTime.toMinutes() * 60)) + 1;
            float queryDuration = minLockTime.toMinutes() * 60 / concurrentProcessCount;
            Float readQueryPerSecond = (1 / queryDuration) + 1;

            return new ProvisionedThroughput()
                    .withReadCapacityUnits(readQueryPerSecond.longValue())
                    .withWriteCapacityUnits(writeQueryPerSecond.longValue());
        }
    }

    public class Builder {

        private AmazonDynamoDB amazonDynamoDB;
        private String tableName;
        private ProvisionedThroughput provisionedThroughput;
        private boolean userRequestingToCreateTable;

        public Builder withAmazonDynamodb(AmazonDynamoDB amazonDynamodb) {
            this.amazonDynamoDB = amazonDynamodb;
            return this;
        }

        public Builder createTable(String tableName, ProvisionedThroughput provisionedThroughput) {
            this.tableName = tableName;
            this.provisionedThroughput = provisionedThroughput;
            userRequestingToCreateTable = true;
            return this;
        }

        public Builder withProvisionedTable(String tableName) {
            this.tableName = tableName;
            userRequestingToCreateTable = false;
            return this;
        }

        public DynamodbLockProvider build() {
            // Create Table
            if (userRequestingToCreateTable) {
                createTableIfNotExists(amazonDynamoDB, tableName, provisionedThroughput);
            }

            // Ensure table exists that has to be used. ResourceNotFoundException thrown if does not exist
            DescribeTableRequest request = new DescribeTableRequest().withTableName(tableName);
            DescribeTableResult response = amazonDynamoDB.describeTable(request);

            return new DynamodbLockProvider(amazonDynamoDB, tableName);
        }

        private void createTableIfNotExists(AmazonDynamoDB dynamodb, String tableName, ProvisionedThroughput provisionedThroughput) {

            CreateTableRequest createTableRequest = new CreateTableRequest()
                    .withAttributeDefinitions(
                            new AttributeDefinition(NAME, ScalarAttributeType.S))
                    .withKeySchema(
                            new KeySchemaElement(NAME, KeyType.HASH))
                    .withProvisionedThroughput(provisionedThroughput)
                    .withTableName(tableName);

            if (!TableUtils.createTableIfNotExists(dynamodb, createTableRequest)) {
                throw new RuntimeException(String.format("Table {} couldn't be created", tableName));
            }
            try {
                TableUtils.waitUntilActive(dynamodb, tableName);
            } catch (InterruptedException e) {
                throw new RuntimeException(String.format("Table {} couldn't be created", tableName, e));
            }
        }
    }

}
