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
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBClientLockRepository.OBTAIN_LOCK_CONDITION;
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.SORT;
import static net.javacrumbs.shedlock.support.Utils.toIsoString;

import java.time.Instant;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

class DynamoDBTableLockRepository implements DynamoDBLockRepository {
    private final DynamoDbTable<DynamoDBLockItem> table;
    private final boolean hasSortKey;

    DynamoDBTableLockRepository(DynamoDbTable<DynamoDBLockItem> table) {
        this.table = requireNonNull(table, "table can not be null");
        this.hasSortKey = table.tableSchema().tableMetadata().primarySortKey().isPresent();
    }

    @Override
    public boolean obtainLock(String lockName, Instant lockUntil, Instant lockedAt, String lockedBy) {
        String lockedAtIso = toIsoString(lockedAt);
        DynamoDBLockItem item = keyItem(lockName);
        item.setLockUntil(toIsoString(lockUntil));
        item.setLockedAt(lockedAtIso);
        item.setLockedBy(lockedBy);
        Expression condition = Expression.builder()
                .expression(OBTAIN_LOCK_CONDITION)
                .putExpressionValue(":lockedAt", AttributeValue.fromS(lockedAtIso))
                .build();

        try {
            // Same three situations as DynamoDBClientLockRepository.obtainLock
            table.updateItem(UpdateItemEnhancedRequest.builder(DynamoDBLockItem.class)
                    .item(item)
                    .ignoreNulls(true)
                    .conditionExpression(condition)
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    @Override
    public void releaseLock(String lockName, Instant lockUntil) {
        DynamoDBLockItem item = keyItem(lockName);
        item.setLockUntil(toIsoString(lockUntil));
        // ignoreNulls keeps lockedAt and lockedBy on the stored item
        table.updateItem(UpdateItemEnhancedRequest.builder(DynamoDBLockItem.class)
                .item(item)
                .ignoreNulls(true)
                .build());
    }

    private DynamoDBLockItem keyItem(String lockName) {
        DynamoDBLockItem item = new DynamoDBLockItem();
        item.setId(lockName);
        if (hasSortKey) {
            item.setSortKey(lockName.concat(SORT));
        }
        return item;
    }
}
