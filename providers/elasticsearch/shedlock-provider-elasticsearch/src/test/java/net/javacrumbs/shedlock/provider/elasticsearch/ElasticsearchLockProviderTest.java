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
package net.javacrumbs.shedlock.provider.elasticsearch;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.apache.http.HttpHost;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

import static net.javacrumbs.shedlock.provider.elasticsearch.ElasticsearchLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.elasticsearch.ElasticsearchLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.elasticsearch.ElasticsearchLockProvider.LOCK_UNTIL;
import static net.javacrumbs.shedlock.provider.elasticsearch.ElasticsearchLockProvider.NAME;
import static net.javacrumbs.shedlock.provider.elasticsearch.ElasticsearchLockProvider.SCHEDLOCK_DEFAULT_INDEX;
import static net.javacrumbs.shedlock.provider.elasticsearch.ElasticsearchLockProvider.SCHEDLOCK_DEFAULT_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@Testcontainers
public class ElasticsearchLockProviderTest extends AbstractLockProviderIntegrationTest {

    @Container
    private static final ElasticsearchContainer container = new ElasticsearchContainer();
    private RestHighLevelClient highLevelClient;
    private ElasticsearchLockProvider lockProvider;

    @BeforeEach
    public void setUp() {
        highLevelClient = new RestHighLevelClient(
            RestClient.builder(HttpHost.create(container.getHttpHostAddress()))
        );
        lockProvider = new ElasticsearchLockProvider(highLevelClient);
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
        GetRequest gr = new GetRequest(SCHEDLOCK_DEFAULT_INDEX, SCHEDLOCK_DEFAULT_TYPE, lockName);
        try {
            GetResponse res = highLevelClient.get(gr, RequestOptions.DEFAULT);
            Map<String, Object> m = res.getSourceAsMap();
            assertThat(new Date((Long) m.get(LOCK_UNTIL))).isBeforeOrEqualsTo(now());
            assertThat(new Date((Long) m.get(LOCKED_AT))).isBeforeOrEqualsTo(now());
            assertThat((String) m.get(LOCKED_BY)).isNotBlank();
            assertThat((String) m.get(NAME)).isEqualTo(lockName);
        } catch (IOException e) {
            fail("Call to embedded ES failed.");
        }
    }

    @Override
    protected void assertLocked(String lockName) {
        GetRequest gr = new GetRequest(SCHEDLOCK_DEFAULT_INDEX, SCHEDLOCK_DEFAULT_TYPE, lockName);
        try {
            GetResponse res = highLevelClient.get(gr, RequestOptions.DEFAULT);
            Map<String, Object> m = res.getSourceAsMap();
            assertThat(new Date((Long) m.get(LOCK_UNTIL))).isAfter(now());
            assertThat(new Date((Long) m.get(LOCKED_AT))).isBeforeOrEqualsTo(now());
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
