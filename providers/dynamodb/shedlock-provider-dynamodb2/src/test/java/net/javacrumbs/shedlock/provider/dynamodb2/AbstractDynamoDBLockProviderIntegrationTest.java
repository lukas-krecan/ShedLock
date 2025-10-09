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

import static java.time.Instant.now;
import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider.LOCK_UNTIL;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;

@Testcontainers
public abstract class AbstractDynamoDBLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {
    protected static final String ID = "_id2";

    @Container
    static final DynamoDbContainer dynamoDbContainer =
            new DynamoDbContainer(DockerImageName.parse("amazon/dynamodb-local:2.6.1")).withExposedPorts(8000);

    protected static final String TABLE_NAME = "Shedlock";

    @SuppressWarnings("NullAway")
    protected static DynamoDbClient dynamodb;

    protected static void waitForTableBeingActive() {
        while (getTableStatus() != TableStatus.ACTIVE) {
            // Avoid empty-loop warnings; politely yield while waiting
            Thread.onSpinWait();
        }
    }

    private static TableStatus getTableStatus() {
        return dynamodb.describeTable(
                        DescribeTableRequest.builder().tableName(TABLE_NAME).build())
                .table()
                .tableStatus();
    }

    /**
     * Create a standard AWS v2 SDK client pointing to the local DynamoDb instance
     *
     * @return A DynamoDbClient pointing to the local DynamoDb instance
     */
    protected static DynamoDbClient createClient() {
        String endpoint = "http://" + dynamoDbContainer.getHost() + ":" + dynamoDbContainer.getFirstMappedPort();
        return DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint))
                // The region is meaningless for local DynamoDb but required for client builder
                // validation
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")))
                .region(Region.US_EAST_1)
                .build();
    }

    @Override
    protected void assertUnlocked(String lockName) {
        Map<String, AttributeValue> lockItem = getLockItem(lockName);
        assertThat(getTimestamp(lockItem, LOCK_UNTIL)).isBeforeOrEqualTo(now());
        assertThat(getTimestamp(lockItem, LOCKED_AT)).isBeforeOrEqualTo(now());
        assertThat(requireNonNull(lockItem.get(LOCKED_BY)).s()).isNotEmpty();
    }

    private Instant getTimestamp(Map<String, AttributeValue> lockItem, String name) {
        return Instant.parse(requireNonNull(lockItem.get(name)).s());
    }

    @Override
    protected void assertLocked(String lockName) {
        Map<String, AttributeValue> lockItem = getLockItem(lockName);
        assertThat(getTimestamp(lockItem, LOCK_UNTIL)).isAfter(now());
        assertThat(getTimestamp(lockItem, LOCKED_AT)).isBeforeOrEqualTo(now());
        assertThat(requireNonNull(lockItem.get(LOCKED_BY)).s()).isNotEmpty();
    }

    protected abstract Map<String, AttributeValue> getLockItem(String lockName);

    private static class DynamoDbContainer extends GenericContainer<DynamoDbContainer> {
        DynamoDbContainer(DockerImageName dockerImageName) {
            super(dockerImageName);
        }
    }
}
