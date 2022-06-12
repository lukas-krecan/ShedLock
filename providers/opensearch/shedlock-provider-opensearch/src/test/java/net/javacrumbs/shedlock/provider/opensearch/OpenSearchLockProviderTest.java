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
package net.javacrumbs.shedlock.provider.opensearch;

import net.javacrumbs.container.OpenSearchContainer;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Duration;
import java.util.Date;
import java.util.Map;

import static net.javacrumbs.shedlock.provider.opensearch.OpenSearchLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.opensearch.OpenSearchLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.opensearch.OpenSearchLockProvider.LOCK_UNTIL;
import static net.javacrumbs.shedlock.provider.opensearch.OpenSearchLockProvider.NAME;
import static net.javacrumbs.shedlock.provider.opensearch.OpenSearchLockProvider.SCHEDLOCK_DEFAULT_INDEX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@Testcontainers
public class OpenSearchLockProviderTest extends AbstractLockProviderIntegrationTest {

    @Container
    private static final OpenSearchContainer container = new OpenSearchContainer("opensearchproject/opensearch:1.1.0")
        .withStartupTimeout(Duration.ofMinutes(2))
        .withEnv("plugins.security.disabled", "true")
        .withStartupAttempts(2);
    private RestHighLevelClient highLevelClient;
    private OpenSearchLockProvider lockProvider;

    @BeforeEach
    public void setUp() {
        highLevelClient = new RestHighLevelClient(
            RestClient.builder(HttpHost.create(container.getHttpHostAddress()))
        );
        lockProvider = new OpenSearchLockProvider(highLevelClient);
    }

    @AfterEach
    public void tearDown() throws IOException {
        highLevelClient.close();
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    @Override
    protected void assertUnlocked(String lockName) {
        GetRequest gr = new GetRequest(SCHEDLOCK_DEFAULT_INDEX, lockName);
        try {
            GetResponse res = highLevelClient.get(gr, RequestOptions.DEFAULT);
            Map<String, Object> m = res.getSourceAsMap();
            assertThat(new Date((Long) m.get(LOCK_UNTIL))).isBeforeOrEqualTo(now());
            assertThat(new Date((Long) m.get(LOCKED_AT))).isBeforeOrEqualTo(now());
            assertThat((String) m.get(LOCKED_BY)).isNotBlank();
            assertThat((String) m.get(NAME)).isEqualTo(lockName);
        } catch (IOException e) {
            fail("Call to embedded ES failed.");
        }
    }

    @Override
    protected void assertLocked(String lockName) {
        GetRequest gr = new GetRequest(SCHEDLOCK_DEFAULT_INDEX, lockName);
        try {
            GetResponse res = highLevelClient.get(gr, RequestOptions.DEFAULT);
            Map<String, Object> m = res.getSourceAsMap();
            assertThat(new Date((Long) m.get(LOCK_UNTIL))).isAfter(now());
            assertThat(new Date((Long) m.get(LOCKED_AT))).isBeforeOrEqualTo(now());
            assertThat((String) m.get(LOCKED_BY)).isNotBlank();
            assertThat((String) m.get(NAME)).isEqualTo(lockName);
        } catch (IOException e) {
            fail("Call to embedded ES failed.");
        }
    }

    private Date now() {
        return new Date();
    }
}
