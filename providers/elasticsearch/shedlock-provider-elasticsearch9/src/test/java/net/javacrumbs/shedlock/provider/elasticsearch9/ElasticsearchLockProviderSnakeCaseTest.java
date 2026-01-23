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
package net.javacrumbs.shedlock.provider.elasticsearch9;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test for ElasticsearchLockProvider with SNAKE_CASE field names.
 * This verifies the fix for Issue #2007 where JsonpMapper with SNAKE_CASE naming
 * strategy caused field name mismatches.
 */
@Testcontainers
class ElasticsearchLockProviderSnakeCaseTest {

    private static final DockerImageName DOCKER_IMAGE_NAME = DockerImageName.parse(
                    "docker.elastic.co/elasticsearch/elasticsearch")
            .withTag("7.17.28");

    @Container
    private static final ElasticsearchContainer container = new ElasticsearchContainer(DOCKER_IMAGE_NAME);

    private static final String SNAKE_CASE_INDEX = "shedlock_snake_case";
    private static final DocumentFieldNames SNAKE_CASE_FIELDS = DocumentFieldNames.SNAKE_CASE;

    private ElasticsearchClient client;
    private ElasticsearchLockProvider lockProvider;

    @BeforeEach
    void setUp() {
        client = ElasticsearchClient.of(
                b -> b.host("http://" + container.getHttpHostAddress()).usernameAndPassword("elastic", "changeme"));

        lockProvider = new ElasticsearchLockProvider(ElasticsearchLockProvider.Configuration.builder()
                .withClient(client)
                .withIndex(SNAKE_CASE_INDEX)
                .withFieldNames(SNAKE_CASE_FIELDS)
                .build());
    }

    @Test
    void shouldAcquireLockWithSnakeCaseFieldNames() {
        String lockName = "snake_case_lock_test";
        LockConfiguration lockConfiguration =
                new LockConfiguration(Instant.now(), lockName, Duration.ofMinutes(5), Duration.ZERO);

        Optional<SimpleLock> lock = lockProvider.lock(lockConfiguration);

        assertThat(lock).isPresent();
        assertDocumentHasSnakeCaseFields(lockName);

        lock.get().unlock();
        assertLockIsReleased(lockName);
    }

    @Test
    void shouldPreventConcurrentLockAcquisitionWithSnakeCaseFieldNames() {
        String lockName = "snake_case_concurrent_test";
        LockConfiguration lockConfiguration =
                new LockConfiguration(Instant.now(), lockName, Duration.ofMinutes(5), Duration.ZERO);

        Optional<SimpleLock> firstLock = lockProvider.lock(lockConfiguration);
        assertThat(firstLock).isPresent();

        // Second attempt should fail
        Optional<SimpleLock> secondLock = lockProvider.lock(lockConfiguration);
        assertThat(secondLock).isEmpty();

        // After unlock, should be able to acquire again
        firstLock.get().unlock();
        Optional<SimpleLock> thirdLock = lockProvider.lock(lockConfiguration);
        assertThat(thirdLock).isPresent();
        thirdLock.get().unlock();
    }

    @Test
    void shouldStoreDocumentWithCorrectSnakeCaseFieldNames() {
        String lockName = "snake_case_field_verification_test";
        LockConfiguration lockConfiguration =
                new LockConfiguration(Instant.now(), lockName, Duration.ofMinutes(5), Duration.ZERO);

        Optional<SimpleLock> lock = lockProvider.lock(lockConfiguration);
        assertThat(lock).isPresent();

        // Verify the document structure uses snake_case field names
        GetRequest request = GetRequest.of(gr -> gr.index(SNAKE_CASE_INDEX).id(lockName));
        try {
            GetResponse<Map<String, Object>> response = client.get(request, (Type) Map.class);
            Map<String, Object> source = requireNonNull(response.source());

            // Verify snake_case fields exist (not camelCase)
            assertThat(source).containsKey("lock_until");
            assertThat(source).containsKey("locked_at");
            assertThat(source).containsKey("locked_by");
            assertThat(source).containsKey("name");

            // Verify camelCase fields do NOT exist
            assertThat(source).doesNotContainKey("lockUntil");
            assertThat(source).doesNotContainKey("lockedAt");
            assertThat(source).doesNotContainKey("lockedBy");

        } catch (IOException e) {
            fail("Call to Elasticsearch failed: " + e.getMessage());
        }

        lock.get().unlock();
    }

    private void assertDocumentHasSnakeCaseFields(String lockName) {
        GetRequest request = GetRequest.of(gr -> gr.index(SNAKE_CASE_INDEX).id(lockName));
        try {
            GetResponse<Map<String, Object>> response = client.get(request, (Type) Map.class);
            Map<String, Object> source = requireNonNull(response.source());

            assertThat(getInstant(source, SNAKE_CASE_FIELDS.lockUntil())).isAfter(Instant.now());
            assertThat(getInstant(source, SNAKE_CASE_FIELDS.lockedAt())).isBeforeOrEqualTo(Instant.now());
            assertThat((String) source.get(SNAKE_CASE_FIELDS.lockedBy())).isNotBlank();
            assertThat((String) source.get(SNAKE_CASE_FIELDS.name())).isEqualTo(lockName);
        } catch (IOException e) {
            fail("Call to Elasticsearch failed: " + e.getMessage());
        }
    }

    private void assertLockIsReleased(String lockName) {
        GetRequest request = GetRequest.of(gr -> gr.index(SNAKE_CASE_INDEX).id(lockName));
        try {
            GetResponse<Map<String, Object>> response = client.get(request, (Type) Map.class);
            Map<String, Object> source = requireNonNull(response.source());

            assertThat(getInstant(source, SNAKE_CASE_FIELDS.lockUntil())).isBeforeOrEqualTo(Instant.now());
        } catch (IOException e) {
            fail("Call to Elasticsearch failed: " + e.getMessage());
        }
    }

    private static Instant getInstant(Map<String, Object> source, String key) {
        return Instant.ofEpochMilli((Long) requireNonNull(source.get(key)));
    }
}
