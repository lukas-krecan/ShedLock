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
import static net.javacrumbs.shedlock.provider.elasticsearch9.ElasticsearchLockProvider.SCHEDLOCK_DEFAULT_INDEX;
import static net.javacrumbs.shedlock.test.support.DockerCleaner.removeImageInCi;
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
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class ElasticsearchLockProviderTest extends AbstractLockProviderIntegrationTest {

    private static final DockerImageName DOCKER_IMAGE_NAME = DockerImageName.parse(
                    "docker.elastic.co/elasticsearch/elasticsearch")
            .withTag("7.17.28");

    @Container
    static final ElasticsearchContainer container = new ElasticsearchContainer(DOCKER_IMAGE_NAME);

    private static final DocumentFieldNames DEFAULT_FIELDS = DocumentFieldNames.DEFAULT;

    private ElasticsearchClient client;
    private ElasticsearchLockProvider lockProvider;

    @BeforeEach
    public void setUp() {
        client = ElasticsearchClient.of(
                b -> b.host("http://" + container.getHttpHostAddress()).usernameAndPassword("elastic", "changeme"));
        lockProvider = new ElasticsearchLockProvider(client);
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    @AfterAll
    public static void removeImage() {
        removeImageInCi(DOCKER_IMAGE_NAME.asCanonicalNameString());
    }

    @Override
    protected void assertUnlocked(String lockName) {
        assertDocumentState(lockName, SCHEDLOCK_DEFAULT_INDEX, DEFAULT_FIELDS, false);
    }

    @Override
    protected void assertLocked(String lockName) {
        assertDocumentState(lockName, SCHEDLOCK_DEFAULT_INDEX, DEFAULT_FIELDS, true);
    }

    private void assertDocumentState(String lockName, String index, DocumentFieldNames fields, boolean locked) {
        GetRequest request = GetRequest.of(gr -> gr.index(index).id(lockName));
        try {
            GetResponse<Map<String, Object>> response = client.get(request, (Type) Map.class);
            Map<String, Object> source = requireNonNull(response.source());
            if (locked) {
                assertThat(getInstant(source, fields.lockUntil())).isAfter(now());
            } else {
                assertThat(getInstant(source, fields.lockUntil())).isBeforeOrEqualTo(now());
            }
            assertThat(getInstant(source, fields.lockedAt())).isBeforeOrEqualTo(now());
            assertThat((String) source.get(fields.lockedBy())).isNotBlank();
            assertThat((String) source.get(fields.name())).isEqualTo(lockName);
        } catch (IOException e) {
            fail("Call to embedded ES failed.");
        }
    }

    private static Instant getInstant(Map<String, Object> source, String key) {
        return Instant.ofEpochMilli((Long) requireNonNull(source.get(key)));
    }

    private Instant now() {
        return Instant.now();
    }

    /**
     * Nested tests for SNAKE_CASE field names.
     * This verifies the fix for Issue #2007 where JsonpMapper with SNAKE_CASE naming
     * strategy caused field name mismatches.
     */
    @Nested
    class SnakeCaseFieldNamesTest {

        private static final String SNAKE_CASE_INDEX = "shedlock_snake_case";
        private static final DocumentFieldNames SNAKE_CASE_FIELDS = DocumentFieldNames.SNAKE_CASE;

        private ElasticsearchLockProvider snakeCaseLockProvider;

        @BeforeEach
        void setUpSnakeCase() {
            snakeCaseLockProvider = new ElasticsearchLockProvider(ElasticsearchLockProvider.Configuration.builder()
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

            Optional<SimpleLock> lock = snakeCaseLockProvider.lock(lockConfiguration);

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

            Optional<SimpleLock> firstLock = snakeCaseLockProvider.lock(lockConfiguration);
            assertThat(firstLock).isPresent();

            // Second attempt should fail
            Optional<SimpleLock> secondLock = snakeCaseLockProvider.lock(lockConfiguration);
            assertThat(secondLock).isEmpty();

            // After unlock, should be able to acquire again
            firstLock.get().unlock();
            Optional<SimpleLock> thirdLock = snakeCaseLockProvider.lock(lockConfiguration);
            assertThat(thirdLock).isPresent();
            thirdLock.get().unlock();
        }

        @Test
        void shouldStoreDocumentWithCorrectSnakeCaseFieldNames() {
            String lockName = "snake_case_field_verification_test";
            LockConfiguration lockConfiguration =
                    new LockConfiguration(Instant.now(), lockName, Duration.ofMinutes(5), Duration.ZERO);

            Optional<SimpleLock> lock = snakeCaseLockProvider.lock(lockConfiguration);
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
    }
}
