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
package net.javacrumbs.shedlock.provider.valkey.glide;

import static net.javacrumbs.shedlock.provider.redis.testsupport.RedisContainer.ENV;
import static net.javacrumbs.shedlock.provider.redis.testsupport.RedisContainer.PORT;

import glide.api.GlideClient;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import java.util.concurrent.ExecutionException;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.provider.redis.testsupport.AbstractRedisIntegrationTest;
import net.javacrumbs.shedlock.provider.redis.testsupport.AbstractRedisSafeUpdateIntegrationTest;
import net.javacrumbs.shedlock.provider.redis.testsupport.RedisContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class ValkeyGlideLockProviderIntegrationTest {

    @Container
    public static final RedisContainer redis = new RedisContainer(PORT);

    private static GlideClient createClient() throws ExecutionException, InterruptedException {
        GlideClientConfiguration config = GlideClientConfiguration.builder()
                .address(NodeAddress.builder()
                        .host(redis.getHost())
                        .port(redis.getFirstMappedPort())
                        .build())
                .build();
        return GlideClient.createClient(config).get();
    }

    @Nested
    class Standalone extends AbstractRedisIntegrationTest {

        private ExtensibleLockProvider lockProvider;
        private GlideClient client;

        @BeforeEach
        public void createLockProvider() throws Exception {
            client = createClient();
            lockProvider = new ValkeyGlideLockProvider(client, ENV);
        }

        @AfterEach
        public void closeClient() throws Exception {
            if (client != null) {
                client.close();
            }
        }

        @Override
        protected String getLock(String lockName) {
            try {
                return client.get(buildKey(lockName, ENV)).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (ExecutionException e) {
                throw new IllegalStateException(e.getCause());
            }
        }

        @Override
        protected ExtensibleLockProvider getLockProvider() {
            return lockProvider;
        }
    }

    @Nested
    class SafeUpdate extends AbstractRedisSafeUpdateIntegrationTest {

        private ExtensibleLockProvider lockProvider;
        private GlideClient client;

        @BeforeEach
        public void createLockProvider() throws Exception {
            client = createClient();
            lockProvider = new ValkeyGlideLockProvider(client, ENV, true);
        }

        @AfterEach
        public void closeClient() throws Exception {
            if (client != null) {
                client.close();
            }
        }

        @Override
        protected String getLock(String lockName) {
            try {
                return client.get(buildKey(lockName, ENV)).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (ExecutionException e) {
                throw new IllegalStateException(e.getCause());
            }
        }

        @Override
        protected ExtensibleLockProvider getLockProvider() {
            return lockProvider;
        }
    }
}
