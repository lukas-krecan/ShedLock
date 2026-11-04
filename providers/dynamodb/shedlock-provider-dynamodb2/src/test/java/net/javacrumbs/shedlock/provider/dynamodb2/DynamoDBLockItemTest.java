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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

class DynamoDBLockItemTest {

    @Test
    @DisplayName("given the bean schema then it maps _id as the only key plus the three lock attributes")
    void beanSchema() {
        TableSchema<DynamoDBLockItem> schema = TableSchema.fromBean(DynamoDBLockItem.class);

        assertThat(schema.tableMetadata().primaryPartitionKey()).isEqualTo("_id");
        assertThat(schema.tableMetadata().primarySortKey()).isEmpty();
        assertThat(schema.attributeNames()).containsExactlyInAnyOrder("_id", "lockUntil", "lockedAt", "lockedBy");
    }

    @Test
    @DisplayName(
            "given custom key names when building the static schema then an item maps to both keys and the three lock"
                    + " attributes")
    void staticSchemaWithSortKey() {
        TableSchema<DynamoDBLockItem> schema = DynamoDBLockItem.tableSchema("p", "s");

        assertThat(schema.tableMetadata().primaryPartitionKey()).isEqualTo("p");
        assertThat(schema.tableMetadata().primarySortKey()).contains("s");
        assertThat(schema.itemToMap(fullItem(), true).keySet())
                .containsExactlyInAnyOrder("p", "s", "lockUntil", "lockedAt", "lockedBy");
    }

    @Test
    @DisplayName("given no sort key name when building the static schema then an item maps to the partition key and the"
            + " three lock attributes")
    void staticSchemaWithoutSortKey() {
        TableSchema<DynamoDBLockItem> schema = DynamoDBLockItem.tableSchema("p", null);

        assertThat(schema.tableMetadata().primarySortKey()).isEmpty();
        assertThat(schema.itemToMap(fullItem(), true).keySet())
                .containsExactlyInAnyOrder("p", "lockUntil", "lockedAt", "lockedBy");
    }

    @Test
    @DisplayName("given a null table when building the provider then it fails fast")
    @SuppressWarnings("NullAway")
    void nullTable() {
        assertThatThrownBy(() -> new DynamoDBEnhancedLockProvider(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("table can not be null");
    }

    private static DynamoDBLockItem fullItem() {
        DynamoDBLockItem item = new DynamoDBLockItem();
        item.setId("name");
        item.setSortKey("name_SortKey");
        item.setLockUntil("2026-01-01T00:00:00Z");
        item.setLockedAt("2026-01-01T00:00:00Z");
        item.setLockedBy("host");
        return item;
    }
}
