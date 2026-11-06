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

import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static net.javacrumbs.shedlock.support.Utils.toIsoString;

import java.util.Optional;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.Utils;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

/**
 * Distributed lock using a DynamoDB enhanced-client table. Requires
 * <code>software.amazon.awssdk:dynamodb-enhanced</code>.
 *
 * <p>Uses the same item format as the standard DynamoDB provider; both providers can share a table
 * when the key names match.
 */
public class DynamoDBEnhancedLockProvider implements LockProvider {
    static final String LOCK_UNTIL = "lockUntil";
    static final String LOCKED_AT = "lockedAt";
    static final String LOCKED_BY = "lockedBy";
    static final String ID = "_id";
    private static final String SORT = "_SortKey";
    private static final String OBTAIN_LOCK_CONDITION =
            LOCK_UNTIL + " <= :lockedAt or attribute_not_exists(" + LOCK_UNTIL + ")";

    private final DynamoDbTable<DynamoDBLockItem> table;
    private final boolean hasSortKey;
    private final String hostname;

    /**
     * Uses a table with <code>_id</code> as its partition key and no sort key.
     *
     * @param enhancedClient DynamoDB enhanced client
     * @param tableName lock table name; the table must already exist
     */
    public DynamoDBEnhancedLockProvider(DynamoDbEnhancedClient enhancedClient, String tableName) {
        this(enhancedClient, tableName, ID, null);
    }

    /**
     * Uses a table with custom key names.
     *
     * @param enhancedClient DynamoDB enhanced client
     * @param tableName lock table name; the table must already exist
     * @param partitionKeyName partition key attribute name
     * @param sortKeyName sort key attribute name, or null for a table without a sort key
     */
    public DynamoDBEnhancedLockProvider(
            DynamoDbEnhancedClient enhancedClient,
            String tableName,
            String partitionKeyName,
            @Nullable String sortKeyName) {
        this(requireNonNull(enhancedClient, "enhancedClient can not be null")
                .table(
                        requireNonNull(tableName, "tableName can not be null"),
                        DynamoDBLockItem.tableSchema(partitionKeyName, sortKeyName)));
    }

    /**
     * Uses a table supplied by a framework, for example one created using
     * <code>TableSchema.fromClass(DynamoDBLockItem.class)</code>.
     *
     * @param table lock table mapped to {@link DynamoDBLockItem}; the table must already exist
     */
    public DynamoDBEnhancedLockProvider(DynamoDbTable<DynamoDBLockItem> table) {
        this.table = requireNonNull(table, "table can not be null");
        this.hasSortKey = table.tableSchema().tableMetadata().primarySortKey().isPresent();
        this.hostname = Utils.getHostname();
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        String lockedAtIso = toIsoString(now());
        DynamoDBLockItem item = keyItem(lockConfiguration.getName());
        item.setLockUntil(toIsoString(lockConfiguration.getLockAtMostUntil()));
        item.setLockedAt(lockedAtIso);
        item.setLockedBy(hostname);
        Expression condition = Expression.builder()
                .expression(OBTAIN_LOCK_CONDITION)
                .putExpressionValue(":lockedAt", AttributeValue.fromS(lockedAtIso))
                .build();

        try {
            table.updateItem(UpdateItemEnhancedRequest.builder(DynamoDBLockItem.class)
                    .item(item)
                    .ignoreNulls(true)
                    .conditionExpression(condition)
                    .build());
            return Optional.of(new DynamoDBLock(table, lockConfiguration, keyItem(lockConfiguration.getName())));
        } catch (ConditionalCheckFailedException e) {
            return Optional.empty();
        } catch (Exception e) {
            throw new LockException("Error on lock", e);
        }
    }

    private DynamoDBLockItem keyItem(String lockName) {
        DynamoDBLockItem item = new DynamoDBLockItem();
        item.setId(lockName);
        if (hasSortKey) {
            item.setSortKey(lockName.concat(SORT));
        }
        return item;
    }

    private static final class DynamoDBLock extends AbstractSimpleLock {
        private final DynamoDbTable<DynamoDBLockItem> table;
        private final DynamoDBLockItem key;

        private DynamoDBLock(
                DynamoDbTable<DynamoDBLockItem> table, LockConfiguration lockConfiguration, DynamoDBLockItem key) {
            super(lockConfiguration);
            this.table = table;
            this.key = key;
        }

        @Override
        public void doUnlock() {
            // Set lockUntil to now or lockAtLeastUntil whichever is later.
            key.setLockUntil(toIsoString(lockConfiguration.getUnlockTime()));
            // ignoreNulls keeps lockedAt and lockedBy on the stored item.
            table.updateItem(UpdateItemEnhancedRequest.builder(DynamoDBLockItem.class)
                    .item(key)
                    .ignoreNulls(true)
                    .build());
        }
    }
}
