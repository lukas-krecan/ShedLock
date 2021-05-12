/**
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.dynamodb;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.dynamodb.DynaliteContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static net.javacrumbs.shedlock.provider.dynamodb.DynamoDBLockProvider.ID;
import static net.javacrumbs.shedlock.provider.dynamodb.DynamoDBLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.dynamodb.DynamoDBLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.dynamodb.DynamoDBLockProvider.LOCK_UNTIL;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>Note</b>: If tests fail when build in your IDE first unpack native
 * dependencies by running
 * <pre>
 * mvn verify -pl providers/dynamodb/shedlock-provider-dynamodb --also-make
 * </pre>
 * from project root and ensure that <code>sqlite4java.library.path</code>
 * is set to <code>./target/dependencies</code>.
 */
@Testcontainers
public class DynamoDBLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {
    @Container
    public static final DynaliteContainer dynamoDB = new DynaliteContainer();

    private static final String TABLE_NAME = "Shedlock";
    private static Table lockTable;

    @BeforeAll
    static void createLockProvider() throws InterruptedException {
        lockTable = DynamoDBUtils.createLockTable(dynamoDB.getClient(), TABLE_NAME, new ProvisionedThroughput(1L, 1L));
        lockTable.waitForActive();
    }

    @AfterEach
    public void truncateLockTable() {
        for (Item item : lockTable.scan(new ScanSpec())) {
            lockTable.deleteItem(new PrimaryKey(ID, item.getString(ID)));
        }
    }

    @Override
    protected LockProvider getLockProvider() {
        Table table = new DynamoDB(dynamoDB.getClient()).getTable(TABLE_NAME);
        return new DynamoDBLockProvider(table);
    }

    @Override
    protected void assertUnlocked(String lockName) {
        Item lockItem = getLockItem(lockName);
        assertThat(fromIsoString(lockItem.getString(LOCK_UNTIL))).isBeforeOrEqualTo(now());
        assertThat(fromIsoString(lockItem.getString(LOCKED_AT))).isBeforeOrEqualTo(now());
        assertThat(lockItem.getString(LOCKED_BY)).isNotEmpty();
    }

    private Instant now() {
        return Instant.now();
    }

    @Override
    protected void assertLocked(String lockName) {
        Item lockItem = getLockItem(lockName);
        assertThat(fromIsoString(lockItem.getString(LOCK_UNTIL))).isAfter(now());
        assertThat(fromIsoString(lockItem.getString(LOCKED_AT))).isBeforeOrEqualTo(now());
        assertThat(lockItem.getString(LOCKED_BY)).isNotEmpty();
    }

    private Instant fromIsoString(String isoString) {
        return Instant.parse(isoString);
    }

    private Table getLockTable() {
        return new DynamoDB(dynamoDB.getClient()).getTable(TABLE_NAME);
    }

    private Item getLockItem(String lockName) {
        return getLockTable().getItem(ID, lockName);
    }
}
