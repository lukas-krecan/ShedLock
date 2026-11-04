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

import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.LOCK_UNTIL;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

public class DynamoDBEnhancedLockProviderIntegrationTest extends AbstractDynamoDBLockProviderIntegrationTest {
    // The inherited ID is "_id2"; the bean schema is fixed on the provider's default "_id".
    private static final String PARTITION_KEY = DynamoDBLockProvider.ID;

    @BeforeAll
    static void createLockTable() {
        dynamodb = createClient();
        DynamoDBUtils.createLockTable(
                dynamodb,
                TABLE_NAME,
                ProvisionedThroughput.builder()
                        .readCapacityUnits(1L)
                        .writeCapacityUnits(1L)
                        .build());
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
                    .key(Map.of(PARTITION_KEY, item.get(PARTITION_KEY)))
                    .build());
        }
    }

    @Override
    protected LockProvider getLockProvider() {
        return new DynamoDBEnhancedLockProvider(DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamodb)
                .build()
                .table(TABLE_NAME, TableSchema.fromBean(DynamoDBLockItem.class)));
    }

    @Override
    protected Map<String, AttributeValue> getLockItem(String lockName) {
        GetItemRequest request = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of(PARTITION_KEY, AttributeValue.builder().s(lockName).build()))
                .build();
        return dynamodb.getItem(request).item();
    }

    @Test
    public void shouldKeepLockedAtAndLockedByAfterUnlock() {
        SimpleLock lock = getLockProvider().lock(lockConfig("keep")).orElseThrow();

        lock.unlock();

        assertThat(getLockItem("keep")).containsKeys(LOCK_UNTIL, LOCKED_AT, LOCKED_BY);
        assertUnlocked("keep");
    }
}
