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
package net.javacrumbs.shedlock.provider.elasticsearch8;

import static net.javacrumbs.shedlock.provider.elasticsearch8.ElasticsearchLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.elasticsearch8.ElasticsearchLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.elasticsearch8.ElasticsearchLockProvider.LOCK_UNTIL;
import static net.javacrumbs.shedlock.provider.elasticsearch8.ElasticsearchLockProvider.NAME;
import static net.javacrumbs.shedlock.provider.elasticsearch8.ElasticsearchLockProvider.SCHEDLOCK_DEFAULT_INDEX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class ElasticsearchLockProviderTest extends AbstractLockProviderIntegrationTest {

    private static final DockerImageName DEFAULT_IMAGE_NAME =
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch");

    @Container
    private static final ElasticsearchContainer container =
            new ElasticsearchContainer(DEFAULT_IMAGE_NAME.withTag("7.17.5")).withPassword("elastic1234");

    private ElasticsearchClient client;
    private ElasticsearchLockProvider lockProvider;

    @BeforeEach
    public void setUp() {
        client = ElasticsearchClient.of(b -> b
            .host("http://" + container.getHttpHostAddress())
            .usernameAndPassword("elastic", "elastic1234")
        );
        lockProvider = new ElasticsearchLockProvider(client);
    }

    private ObjectMapper objectMapper() {
        JavaTimeModule module = new JavaTimeModule();
        module.addSerializer(
                LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .registerModule(module);
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    @Override
    protected void assertUnlocked(String lockName) {
        GetRequest request =
                GetRequest.of(gr -> gr.index(SCHEDLOCK_DEFAULT_INDEX).id(lockName));
        try {
            GetResponse<Map> response = client.get(request, Map.class);
            Map source = response.source();
            assertThat(new Date((Long) source.get(LOCK_UNTIL))).isBeforeOrEqualsTo(now());
            assertThat(new Date((Long) source.get(LOCKED_AT))).isBeforeOrEqualsTo(now());
            assertThat((String) source.get(LOCKED_BY)).isNotBlank();
            assertThat((String) source.get(NAME)).isEqualTo(lockName);
        } catch (IOException e) {
            fail("Call to embedded ES failed.");
        }
    }

    @Override
    protected void assertLocked(String lockName) {
        GetRequest request =
                GetRequest.of(gr -> gr.index(SCHEDLOCK_DEFAULT_INDEX).id(lockName));
        try {
            GetResponse<Map> response = client.get(request, Map.class);
            Map source = response.source();
            assertThat(new Date((Long) source.get(LOCK_UNTIL))).isAfter(now());
            assertThat(new Date((Long) source.get(LOCKED_AT))).isBeforeOrEqualsTo(now());
            assertThat((String) source.get(LOCKED_BY)).isNotBlank();
            assertThat((String) source.get(NAME)).isEqualTo(lockName);
        } catch (IOException e) {
            fail("Call to embedded ES failed.");
        }
    }

    private Date now() {
        return new Date();
    }
}
