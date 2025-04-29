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

import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.SORT;

import java.util.List;
import java.util.Map;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

@Testcontainers
public class DynamoDBLockProviderSortKeyIntegrationTest extends AbstractDynamoDBLockProviderIntegrationTest {
    private static final String SORT_KEY = "_sk";

    @BeforeAll
    static void createLockTable() {
        dynamodb = createClient();
        CreateTableRequest request = CreateTableRequest.builder()
                .tableName(TABLE_NAME)
                .keySchema(
                        KeySchemaElement.builder()
                                .attributeName(ID)
                                .keyType(KeyType.HASH)
                                .build(),
                        KeySchemaElement.builder()
                                .attributeName(SORT_KEY)
                                .keyType(KeyType.RANGE)
                                .build())
                .attributeDefinitions(
                        AttributeDefinition.builder()
                                .attributeName(ID)
                                .attributeType(ScalarAttributeType.S)
                                .build(),
                        AttributeDefinition.builder()
                                .attributeName(SORT_KEY)
                                .attributeType(ScalarAttributeType.S)
                                .build())
                .provisionedThroughput(ProvisionedThroughput.builder()
                        .readCapacityUnits(1L)
                        .writeCapacityUnits(1L)
                        .build())
                .build();
        dynamodb.createTable(request);

        waitForTableBeingActive();
    }

    @AfterEach
    public void truncateLockTable() {
        List<Map<String, AttributeValue>> items = dynamodb.scan(
                        ScanRequest.builder().tableName(TABLE_NAME).build())
                .items();
        for (Map<String, AttributeValue> item : items) {
            dynamodb.deleteItem(DeleteItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(ID, item.get(ID), SORT_KEY, item.get(SORT_KEY)))
                    .build());
        }
    }

    @Override
    protected LockProvider getLockProvider() {
        return new DynamoDBLockProvider(dynamodb, TABLE_NAME, ID, SORT_KEY);
    }

    @Override
    protected Map<String, AttributeValue> getLockItem(String lockName) {
        GetItemRequest request = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of(
                        ID,
                        AttributeValue.builder().s(lockName).build(),
                        SORT_KEY,
                        AttributeValue.builder().s(lockName.concat(SORT)).build()))
                .build();
        return dynamodb.getItem(request).item();
    }
}
