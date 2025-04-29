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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

public class DynamoDBLockProviderIntegrationTest extends AbstractDynamoDBLockProviderIntegrationTest {
    private static final String ID = "_id2";

    @BeforeAll
    static void createLockTable() {
        dynamodb = createClient();
        DynamoDBUtils.createLockTable(
                dynamodb,
                TABLE_NAME,
                ProvisionedThroughput.builder()
                        .readCapacityUnits(1L)
                        .writeCapacityUnits(1L)
                        .build(),
                ID);
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
                    .key(Collections.singletonMap(ID, item.get(ID)))
                    .build());
        }
    }

    @Override
    protected LockProvider getLockProvider() {
        return new DynamoDBLockProvider(dynamodb, TABLE_NAME, ID);
    }

    @Override
    protected Map<String, AttributeValue> getLockItem(String lockName) {
        GetItemRequest request = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Collections.singletonMap(
                        ID, AttributeValue.builder().s(lockName).build()))
                .build();
        return dynamodb.getItem(request).item();
    }
}
