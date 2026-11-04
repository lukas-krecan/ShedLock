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
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.ID;
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.LOCK_UNTIL;
import static software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags.primaryPartitionKey;
import static software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags.primarySortKey;

import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticTableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.utils.StringUtils;

/**
 * Lock item for {@link DynamoDBEnhancedLockProvider}.
 * Use <code>TableSchema.fromBean(DynamoDBLockItem.class)</code> for a hash-only table keyed on <code>_id</code>, or
 * {@link #tableSchema(String, String)} for custom key names and a sort key.
 */
@DynamoDbBean
public class DynamoDBLockItem {
    private @Nullable String id;
    private @Nullable String sortKey;
    private @Nullable String lockUntil;
    private @Nullable String lockedAt;
    private @Nullable String lockedBy;

    /** Static schema for custom key names; a null sort key name means a hash-only table. */
    public static TableSchema<DynamoDBLockItem> tableSchema(String partitionKeyName, @Nullable String sortKeyName) {
        requireNonNull(partitionKeyName, "partitionKeyName can not be null");
        StaticTableSchema.Builder<DynamoDBLockItem> builder = StaticTableSchema.builder(DynamoDBLockItem.class)
                .newItemSupplier(DynamoDBLockItem::new)
                .addAttribute(
                        String.class,
                        a -> a.name(partitionKeyName)
                                .getter(DynamoDBLockItem::getId)
                                .setter(DynamoDBLockItem::setId)
                                .tags(primaryPartitionKey()))
                .addAttribute(
                        String.class,
                        a -> a.name(LOCK_UNTIL)
                                .getter(DynamoDBLockItem::getLockUntil)
                                .setter(DynamoDBLockItem::setLockUntil))
                .addAttribute(
                        String.class,
                        a -> a.name(LOCKED_AT)
                                .getter(DynamoDBLockItem::getLockedAt)
                                .setter(DynamoDBLockItem::setLockedAt))
                .addAttribute(
                        String.class,
                        a -> a.name(LOCKED_BY)
                                .getter(DynamoDBLockItem::getLockedBy)
                                .setter(DynamoDBLockItem::setLockedBy));
        if (StringUtils.isNotBlank(sortKeyName)) {
            builder.addAttribute(
                    String.class,
                    a -> a.name(sortKeyName)
                            .getter(DynamoDBLockItem::getSortKey)
                            .setter(DynamoDBLockItem::setSortKey)
                            .tags(primarySortKey()));
        }
        return builder.build();
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute(ID)
    public @Nullable String getId() {
        return id;
    }

    public void setId(@Nullable String id) {
        this.id = id;
    }

    // The bean schema is hash-only; only the static schema maps the sort key.
    @DynamoDbIgnore
    public @Nullable String getSortKey() {
        return sortKey;
    }

    public void setSortKey(@Nullable String sortKey) {
        this.sortKey = sortKey;
    }

    public @Nullable String getLockUntil() {
        return lockUntil;
    }

    public void setLockUntil(@Nullable String lockUntil) {
        this.lockUntil = lockUntil;
    }

    public @Nullable String getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(@Nullable String lockedAt) {
        this.lockedAt = lockedAt;
    }

    public @Nullable String getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(@Nullable String lockedBy) {
        this.lockedBy = lockedBy;
    }
}
