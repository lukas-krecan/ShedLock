/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.dynamodb2;

import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.LOCK_UNTIL;
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.SORT;
import static net.javacrumbs.shedlock.support.Utils.toIsoString;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.utils.StringUtils;

class DynamoDBClientLockRepository implements DynamoDBLockRepository {
    private static final String OBTAIN_LOCK_QUERY =
            "set " + LOCK_UNTIL + " = :lockUntil, " + LOCKED_AT + " = :lockedAt, " + LOCKED_BY + " = :lockedBy";
    static final String OBTAIN_LOCK_CONDITION =
            LOCK_UNTIL + " <= :lockedAt or attribute_not_exists(" + LOCK_UNTIL + ")";
    private static final String RELEASE_LOCK_QUERY = "set " + LOCK_UNTIL + " = :lockUntil";

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final String partitionKeyName;
    private final @Nullable String sortKeyName;

    DynamoDBClientLockRepository(
            DynamoDbClient dynamoDbClient, String tableName, String partitionKeyName, @Nullable String sortKeyName) {
        this.dynamoDbClient = requireNonNull(dynamoDbClient, "dynamoDbClient can not be null");
        this.tableName = requireNonNull(tableName, "tableName can not be null");
        this.partitionKeyName = requireNonNull(partitionKeyName, "partitionKeyName can not be null");
        this.sortKeyName = sortKeyName;
    }

    @Override
    public boolean obtainLock(String lockName, Instant lockUntil, Instant lockedAt, String lockedBy) {
        Map<String, AttributeValue> attributeUpdates = Map.of(
                ":lockUntil", attr(toIsoString(lockUntil)),
                ":lockedAt", attr(toIsoString(lockedAt)),
                ":lockedBy", attr(lockedBy));

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(getKey(lockName))
                .updateExpression(OBTAIN_LOCK_QUERY)
                .conditionExpression(OBTAIN_LOCK_CONDITION)
                .expressionAttributeValues(attributeUpdates)
                .returnValues(ReturnValue.UPDATED_NEW)
                .build();

        try {
            // There are three possible situations:
            // 1. The lock document does not exist yet - it is inserted - we have the lock
            // 2. The lock document exists and lockUtil <= now - it is updated - we have the
            // lock
            // 3. The lock document exists and lockUtil > now -
            // ConditionalCheckFailedException is thrown
            dynamoDbClient.updateItem(request);
            return true;
        } catch (ConditionalCheckFailedException e) {
            // Condition failed. This means there was a lock with lockUntil > now.
            return false;
        }
    }

    @Override
    public void releaseLock(String lockName, Instant lockUntil) {
        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(getKey(lockName))
                .updateExpression(RELEASE_LOCK_QUERY)
                .expressionAttributeValues(Map.of(":lockUntil", attr(toIsoString(lockUntil))))
                .returnValues(ReturnValue.UPDATED_NEW)
                .build();

        dynamoDbClient.updateItem(request);
    }

    private Map<String, AttributeValue> getKey(String lockName) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put(partitionKeyName, attr(lockName));
        if (StringUtils.isNotBlank(sortKeyName)) {
            key.put(sortKeyName, attr(lockName.concat(SORT)));
        }
        return key;
    }

    private static AttributeValue attr(String value) {
        return AttributeValue.builder().s(value).build();
    }
}
