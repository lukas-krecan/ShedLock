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
package net.javacrumbs.shedlock.provider.redis.lettuce;

import static net.javacrumbs.shedlock.provider.redis.testsupport.RedisContainer.ENV;
import static net.javacrumbs.shedlock.provider.redis.testsupport.RedisContainer.PORT;
import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.provider.redis.testsupport.AbstractExtensibleLockProviderRedisIntegrationTest;
import net.javacrumbs.shedlock.provider.redis.testsupport.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class LettuceLockProviderIntegrationTest {

    @Container
    public static final RedisContainer redis = new RedisContainer(PORT);

    private static RedisClient createClient() {
        String uri = String.format("redis://%s:%d", redis.getHost(), redis.getFirstMappedPort());
        return RedisClient.create(uri);
    }

    @Nested
    class Cluster extends AbstractExtensibleLockProviderRedisIntegrationTest {

        private ExtensibleLockProvider lockProvider;
        private StatefulRedisConnection<String, String> connection;

        @BeforeEach
        public void createLockProvider() {
            connection = createClient().connect();
            lockProvider = new LettuceLockProvider(connection, ENV);
        }

        @Override
        protected void assertUnlocked(String lockName) {
            assertThat(getLock(lockName)).isNull();
        }

        @Override
        protected void assertLocked(String lockName) {
            assertThat(getLock(lockName)).isNotNull();
        }

        private String getLock(String lockName) {
            return connection.sync().get(LettuceLockProvider.buildKey(lockName, ENV));
        }

        @Override
        protected ExtensibleLockProvider getLockProvider() {
            return lockProvider;
        }
    }

    @Nested
    class Pool extends AbstractExtensibleLockProviderRedisIntegrationTest {

        private ExtensibleLockProvider lockProvider;
        private StatefulRedisConnection<String, String> connection;

        @BeforeEach
        public void createLockProvider() {
            connection = createClient().connect();
            lockProvider = new LettuceLockProvider(connection, ENV);
        }

        @Override
        protected void assertUnlocked(String lockName) {
            assertThat(getLock(lockName)).isNull();
        }

        @Override
        protected void assertLocked(String lockName) {
            assertThat(getLock(lockName)).isNotNull();
        }

        private String getLock(String lockName) {
            return connection.sync().get(LettuceLockProvider.buildKey(lockName, ENV));
        }

        @Override
        protected ExtensibleLockProvider getLockProvider() {
            return lockProvider;
        }
    }
}
