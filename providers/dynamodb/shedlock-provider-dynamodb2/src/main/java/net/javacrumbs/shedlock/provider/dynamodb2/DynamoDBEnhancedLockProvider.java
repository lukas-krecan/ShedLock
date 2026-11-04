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

import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

/**
 * Distributed lock using a DynamoDB enhanced-client table. Requires
 * <code>software.amazon.awssdk:dynamodb-enhanced</code>.
 *
 * <p>Uses the same item format as {@link DynamoDBLockProvider}; both providers can share a table
 * when the key names match.
 */
public class DynamoDBEnhancedLockProvider implements LockProvider {
    private final LockProvider delegate;

    /**
     * Uses DynamoDB to coordinate locks through an enhanced-client table.
     *
     * @param table lock table, see {@link DynamoDBLockItem}
     */
    public DynamoDBEnhancedLockProvider(DynamoDbTable<DynamoDBLockItem> table) {
        this.delegate = new InternalDynamoDBLockProvider(new DynamoDBTableLockRepository(table));
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        return delegate.lock(lockConfiguration);
    }
}
