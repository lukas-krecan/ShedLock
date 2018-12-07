/**
 * Copyright 2009-2018 the original author or authors.
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

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import com.amazonaws.services.dynamodbv2.local.shared.access.AmazonDynamoDBLocal;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import static net.javacrumbs.shedlock.provider.dynamodb.DynamoDBLockProvider.*;
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
public class DynamoDBLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {
    private static AmazonDynamoDBLocal dynamodbFactory;

    private static final String TABLE_NAME = "Shedlock";
    private AmazonDynamoDB dynamodb;

    @Before
    public void createLockProvider() {
        dynamodb = dynamodbFactory.amazonDynamoDB();
        DynamoDBLockProvider.createLockTable(dynamodb, TABLE_NAME, new ProvisionedThroughput(1L, 1L));
    }

    @After
    public void deleteLockTable() {
        dynamodb.deleteTable(TABLE_NAME);
    }

    @Override
    protected LockProvider getLockProvider() {
        return new DynamoDBLockProvider(dynamodb, TABLE_NAME);
    }

    @Override
    protected void assertUnlocked(String lockName) {
        Item lockItem = getLockItem(lockName);
        assertThat(fromIsoString(lockItem.getString(LOCK_UNTIL))).isBeforeOrEqualTo(now());
        assertThat(fromIsoString(lockItem.getString(LOCKED_AT))).isBeforeOrEqualTo(now());
        assertThat(lockItem.getString(LOCKED_BY)).isNotEmpty();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now();
    }

    @Override
    protected void assertLocked(String lockName) {
        Item lockItem = getLockItem(lockName);
        assertThat(fromIsoString(lockItem.getString(LOCK_UNTIL))).isAfter(now());
        assertThat(fromIsoString(lockItem.getString(LOCKED_AT))).isBeforeOrEqualTo(now());
        assertThat(lockItem.getString(LOCKED_BY)).isNotEmpty();
    }

    private OffsetDateTime fromIsoString(String isoString) {
        return OffsetDateTime.parse(isoString, DateTimeFormatter.ISO_DATE_TIME);
    }

    private Table getLockTable() {
        return new DynamoDB(dynamodb).getTable(TABLE_NAME);
    }

    private Item getLockItem(String lockName) {
        return getLockTable().getItem(ID, lockName);
    }

    @BeforeClass
    public static void startDynamoDB() {
        dynamodbFactory = DynamoDBEmbedded.create();
    }

    @AfterClass
    public static void stopDynamoDB() {
        dynamodbFactory.shutdown();
    }
}
