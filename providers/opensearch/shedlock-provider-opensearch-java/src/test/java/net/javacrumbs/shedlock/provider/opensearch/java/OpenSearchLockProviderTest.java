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
import static java.time.Instant.ofEpochMilli;
import static net.javacrumbs.shedlock.provider.opensearch.java.OpenSearchLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.opensearch.java.OpenSearchLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.opensearch.java.OpenSearchLockProvider.LOCK_UNTIL;
import static net.javacrumbs.shedlock.provider.opensearch.java.OpenSearchLockProvider.NAME;
import static net.javacrumbs.shedlock.provider.opensearch.java.OpenSearchLockProvider.SCHEDLOCK_DEFAULT_INDEX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;
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
import org.junit.jupiter.api.BeforeEach;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.opensearch.testcontainers.OpensearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class OpenSearchLockProviderTest extends AbstractLockProviderIntegrationTest {

    @Container
    private static final OpensearchContainer<?> container =
            new OpensearchContainer<>(DockerImageName.parse("opensearchproject/opensearch:2"));

    private OpenSearchClient openSearchClient;
    private OpenSearchLockProvider lockProvider;

    @BeforeEach
    public void setUp() {
        openSearchClient = openSearchClient();
        lockProvider = new OpenSearchLockProvider(openSearchClient);
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void assertUnlocked(String lockName) {
        GetRequest getRequest =
                GetRequest.of(builder -> builder.index(SCHEDLOCK_DEFAULT_INDEX).id(lockName));

        try {
            GetResponse<Object> objectGetResponse = openSearchClient.get(getRequest, Object.class);
            Map<String, Object> sourceData = (Map<String, Object>) objectGetResponse.source();
            assert sourceData != null;
            assertThat(new Date((Long) sourceData.get(LOCK_UNTIL))).isBeforeOrEqualTo(now());
            assertThat(new Date((Long) sourceData.get(LOCKED_AT))).isBeforeOrEqualTo(now());
            assertThat((String) sourceData.get(LOCKED_BY)).isNotBlank();
            assertThat((String) sourceData.get(NAME)).isEqualTo(lockName);
        } catch (IOException e) {
            fail("Call to embedded OS failed.");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void assertLocked(String lockName) {
        GetRequest getRequest =
                GetRequest.of(builder -> builder.index(SCHEDLOCK_DEFAULT_INDEX).id(lockName));

        try {
            GetResponse<Object> getResponse = openSearchClient.get(getRequest, Object.class);
            Map<String, Object> sourceData = (Map<String, Object>) getResponse.source();

            assert sourceData != null;
            assertThat(ofEpochMilli((Long) sourceData.get(LOCK_UNTIL))).isAfter(now());
            assertThat(ofEpochMilli((Long) sourceData.get(LOCKED_AT))).isBeforeOrEqualTo(now());
            assertThat((String) sourceData.get(LOCKED_BY)).isNotBlank();
            assertThat((String) sourceData.get(NAME)).isEqualTo(lockName);
        } catch (IOException e) {
            fail("Call to embedded OS failed.");
        }
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
