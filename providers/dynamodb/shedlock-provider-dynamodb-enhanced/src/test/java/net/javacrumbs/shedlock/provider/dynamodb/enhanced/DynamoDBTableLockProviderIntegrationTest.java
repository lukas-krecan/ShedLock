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
package net.javacrumbs.shedlock.provider.dynamodb.enhanced;

import net.javacrumbs.shedlock.core.LockProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class DynamoDBTableLockProviderIntegrationTest extends DynamoDBEnhancedLockProviderIntegrationTest {
    @Override
    protected LockProvider getLockProvider() {
        var factory = new TableFactory(dynamodb);
        return new DynamoDBEnhancedLockProvider(factory.table(TABLE_NAME, DynamoDBLockItem.class));
    }

    // The framework exposes tables while keeping both clients private.
    private static final class TableFactory {
        private final DynamoDbEnhancedClient client;

        private TableFactory(DynamoDbClient client) {
            this.client =
                    DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
        }

        private <T> DynamoDbTable<T> table(String reference, Class<T> itemClass) {
            return client.table(reference, TableSchema.fromClass(itemClass));
        }
    }
}
