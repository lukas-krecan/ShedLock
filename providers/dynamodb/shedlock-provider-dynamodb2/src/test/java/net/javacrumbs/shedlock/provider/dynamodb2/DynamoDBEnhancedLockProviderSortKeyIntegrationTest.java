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

import net.javacrumbs.shedlock.core.LockProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;

// Reuses the parent's hash+range table, cleanup and raw getItem, so the item must match what the raw client writes.
public class DynamoDBEnhancedLockProviderSortKeyIntegrationTest extends DynamoDBLockProviderSortKeyIntegrationTest {
    private static final String SORT_KEY = "_sk";

    @Override
    protected LockProvider getLockProvider() {
        return new DynamoDBEnhancedLockProvider(DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamodb)
                .build()
                .table(TABLE_NAME, DynamoDBLockItem.tableSchema(ID, SORT_KEY)));
    }
}
