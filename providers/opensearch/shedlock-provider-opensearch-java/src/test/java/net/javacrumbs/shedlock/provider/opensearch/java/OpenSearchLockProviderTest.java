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
package net.javacrumbs.shedlock.provider.opensearch.java;

import static java.time.Instant.now;
import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.provider.opensearch.java.OpenSearchLockProvider.SCHEDLOCK_DEFAULT_INDEX;
import static net.javacrumbs.shedlock.test.support.DockerCleaner.removeImageInCi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Map;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class OpenSearchLockProviderTest extends AbstractLockProviderIntegrationTest {

    private static final String DOCKER_IMAGE = "opensearchproject/opensearch:2";

    @Container
    private static final OpenSearchContainer<?> container =
            new OpenSearchContainer<>(DockerImageName.parse(DOCKER_IMAGE));

    private static final DocumentFieldNames DEFAULT_FIELDS = DocumentFieldNames.DEFAULT;

    private OpenSearchClient openSearchClient;
    private OpenSearchLockProvider lockProvider;

    @BeforeEach
    public void setUp() {
        openSearchClient = openSearchClient();
        lockProvider = new OpenSearchLockProvider(openSearchClient);
    }

    @AfterAll
    public static void removeImage() {
        removeImageInCi(DOCKER_IMAGE);
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void assertUnlocked(String lockName) {
        assertDocumentState(lockName, SCHEDLOCK_DEFAULT_INDEX, DEFAULT_FIELDS, false);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void assertLocked(String lockName) {
        assertDocumentState(lockName, SCHEDLOCK_DEFAULT_INDEX, DEFAULT_FIELDS, true);
    }

    @SuppressWarnings("unchecked")
    private void assertDocumentState(String lockName, String index, DocumentFieldNames fields, boolean locked) {
        GetRequest getRequest = GetRequest.of(builder -> builder.index(index).id(lockName));

        try {
            GetResponse<Object> response = openSearchClient.get(getRequest, Object.class);
            Map<String, Object> sourceData = (Map<String, Object>) response.source();
            assert sourceData != null;
            if (locked) {
                assertThat(getInstant(sourceData, fields.lockUntil())).isAfter(now());
            } else {
                assertThat(getInstant(sourceData, fields.lockUntil())).isBeforeOrEqualTo(now());
            }
            assertThat(getInstant(sourceData, fields.lockedAt())).isBeforeOrEqualTo(now());
            assertThat((String) sourceData.get(fields.lockedBy())).isNotBlank();
            assertThat((String) sourceData.get(fields.name())).isEqualTo(lockName);
        } catch (IOException e) {
            fail("Call to embedded OS failed.");
        }
    }

    private static Instant getInstant(Map<String, Object> sourceData, String key) {
        return Instant.ofEpochMilli((Long) requireNonNull(sourceData.get(key)));
    }

    private OpenSearchClient openSearchClient() {
        OpenSearchTransport openSearchTransport = openSearchTransport();
        return new OpenSearchClient(openSearchTransport);
    }

    private static OpenSearchTransport openSearchTransport() {
        HttpHost httpHost;
        try {
            httpHost = HttpHost.create(container.getHttpHostAddress());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        BasicCredentialsProvider basicCredentialsProvider = getBasicCredentialsProvider(httpHost);

        return ApacheHttpClient5TransportBuilder.builder(httpHost)
                .setHttpClientConfigCallback(
                        httpClientBuilder -> getClientBuilder(httpClientBuilder, basicCredentialsProvider))
                .build();
    }

    private static HttpAsyncClientBuilder getClientBuilder(
            HttpAsyncClientBuilder httpClientBuilder, CredentialsProvider basicCredentialsProvider) {
        return httpClientBuilder
                .setDefaultCredentialsProvider(basicCredentialsProvider)
                .setConnectionManager(
                        PoolingAsyncClientConnectionManagerBuilder.create().build());
    }

    private static BasicCredentialsProvider getBasicCredentialsProvider(HttpHost httpHost) {
        BasicCredentialsProvider basicCredentialsProvider = new BasicCredentialsProvider();
        basicCredentialsProvider.setCredentials(
                new AuthScope(httpHost), new UsernamePasswordCredentials("admin", "admin".toCharArray()));
        return basicCredentialsProvider;
    }
}
