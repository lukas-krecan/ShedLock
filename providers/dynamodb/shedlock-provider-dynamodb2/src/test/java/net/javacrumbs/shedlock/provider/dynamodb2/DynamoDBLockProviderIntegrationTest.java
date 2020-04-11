/**
 * Copyright 2009-2020 the original author or authors.
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
package net.javacrumbs.shedlock.provider.dynamodb2;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;

import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.ID;
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.LOCK_UNTIL;
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

    // using legacy LocalDynamoDb along with AWS SDK2 DynamoDbClient as described here:
    // https://github.com/aws/aws-sdk-java-v2/issues/982
    private static LocalDynamoDb dynamodbFactory;

    private static final String TABLE_NAME = "Shedlock";
    private DynamoDbClient dynamodb;

    @BeforeEach
    public void createLockProvider() {
        dynamodb = dynamodbFactory.createClient();
        DynamoDBUtils.createLockTable(dynamodb, TABLE_NAME, ProvisionedThroughput.builder()
            .readCapacityUnits(1L)
            .writeCapacityUnits(1L)
            .build());
    }

    @AfterEach
    public void deleteLockTable() {
        dynamodb.deleteTable(DeleteTableRequest.builder()
            .tableName(TABLE_NAME)
            .build());
    }

    @Override
    protected LockProvider getLockProvider() {
        return new DynamoDBLockProvider(dynamodb, TABLE_NAME);
    }

    @Override
    protected void assertUnlocked(String lockName) {
        Map<String, AttributeValue> lockItem = getLockItem(lockName);
        assertThat(fromIsoString(lockItem.get(LOCK_UNTIL).s())).isBeforeOrEqualTo(now());
        assertThat(fromIsoString(lockItem.get(LOCKED_AT).s())).isBeforeOrEqualTo(now());
        assertThat(lockItem.get(LOCKED_BY).s()).isNotEmpty();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now();
    }

    @Override
    protected void assertLocked(String lockName) {
        Map<String, AttributeValue> lockItem = getLockItem(lockName);
        assertThat(fromIsoString(lockItem.get(LOCK_UNTIL).s())).isAfter(now());
        assertThat(fromIsoString(lockItem.get(LOCKED_AT).s())).isBeforeOrEqualTo(now());
        assertThat(lockItem.get(LOCKED_BY).s()).isNotEmpty();
    }

    private OffsetDateTime fromIsoString(String isoString) {
        return OffsetDateTime.parse(isoString, DateTimeFormatter.ISO_DATE_TIME);
    }

    private Map<String, AttributeValue> getLockItem(String lockName) {
        GetItemRequest request = GetItemRequest.builder()
            .key(Collections.singletonMap(ID, AttributeValue.builder()
                .s(lockName)
                .build()))
            .build();
        GetItemResponse response = dynamodb.getItem(request);
        return response.item();
    }

    @BeforeAll
    public static void startDynamoDB() {
        dynamodbFactory = new LocalDynamoDb();
        dynamodbFactory.start();
    }

    @AfterAll
    public static void stopDynamoDB() {
        dynamodbFactory.stop();
    }
}
