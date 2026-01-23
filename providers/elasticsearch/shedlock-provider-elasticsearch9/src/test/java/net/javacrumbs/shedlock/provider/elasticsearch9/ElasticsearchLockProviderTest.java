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
import java.time.Instant;
import java.util.Map;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
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
    private static final ElasticsearchContainer container = new ElasticsearchContainer(DOCKER_IMAGE_NAME);

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
}
