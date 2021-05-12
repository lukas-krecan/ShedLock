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
package net.javacrumbs.shedlock.provider.dynamodb2;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.ID;
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.LOCK_UNTIL;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class DynamoDBLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {
    @Container
    public static final DynamoDbContainer dynamoDbContainer =
        new DynamoDbContainer("quay.io/testcontainers/dynalite:v1.2.1-1")
            .withExposedPorts(4567);


    private static final String TABLE_NAME = "Shedlock";
    private static DynamoDbClient dynamodb;

    @BeforeAll
    static void createLockProvider() {
        dynamodb = createClient();
        String lockTable = DynamoDBUtils.createLockTable(
            dynamodb,
            TABLE_NAME,
            ProvisionedThroughput.builder()
                .readCapacityUnits(1L)
                .writeCapacityUnits(1L)
                .build()
        );
        while (getTableStatus(lockTable) != TableStatus.ACTIVE) ;
    }

    private static TableStatus getTableStatus(String lockTable) {
        return dynamodb.describeTable(DescribeTableRequest.builder().tableName(lockTable).build()).table().tableStatus();
    }

    /**
     * Create a standard AWS v2 SDK client pointing to the local DynamoDb instance
     *
     * @return A DynamoDbClient pointing to the local DynamoDb instance
     */
    static DynamoDbClient createClient() {
        String endpoint = "http://" + dynamoDbContainer.getContainerIpAddress() + ":" + dynamoDbContainer.getFirstMappedPort();
        return DynamoDbClient.builder()
            .endpointOverride(URI.create(endpoint))
            // The region is meaningless for local DynamoDb but required for client builder validation
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("dummy-key", "dummy-secret"))
            )
            .build();
    }

    @AfterEach
    public void truncateLockTable() {
        List<Map<String, AttributeValue>> items = dynamodb.scan(ScanRequest.builder().tableName(TABLE_NAME).build()).items();
        for (Map<String, AttributeValue> item : items) {
            dynamodb.deleteItem(DeleteItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Collections.singletonMap(ID, item.get(ID)))
                .build());
        }
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

    private Instant now() {
        return Instant.now();
    }

    @Override
    protected void assertLocked(String lockName) {
        Map<String, AttributeValue> lockItem = getLockItem(lockName);
        assertThat(fromIsoString(lockItem.get(LOCK_UNTIL).s())).isAfter(now());
        assertThat(fromIsoString(lockItem.get(LOCKED_AT).s())).isBeforeOrEqualTo(now());
        assertThat(lockItem.get(LOCKED_BY).s()).isNotEmpty();
    }

    private Instant fromIsoString(String isoString) {
        return Instant.parse(isoString);
    }

    private Map<String, AttributeValue> getLockItem(String lockName) {
        GetItemRequest request = GetItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(Collections.singletonMap(ID, AttributeValue.builder()
                .s(lockName)
                .build()))
            .build();
        GetItemResponse response = dynamodb.getItem(request);
        return response.item();
    }

    private static class DynamoDbContainer extends GenericContainer<DynamoDbContainer> {
        public DynamoDbContainer(String dockerImageName) {
            super(dockerImageName);
        }
    }
}
