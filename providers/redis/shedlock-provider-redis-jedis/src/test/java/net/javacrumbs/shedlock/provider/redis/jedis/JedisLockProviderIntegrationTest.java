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
package net.javacrumbs.shedlock.provider.redis.jedis;

import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractExtensibleLockProviderIntegrationTest;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;

import static net.javacrumbs.shedlock.provider.redis.jedis.RedisContainer.ENV;
import static net.javacrumbs.shedlock.provider.redis.jedis.RedisContainer.PORT;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class JedisLockProviderIntegrationTest  {

    @Container
    public static final RedisContainer redis = new RedisContainer(PORT);

    @Nested
    class Cluster extends AbstractExtensibleLockProviderIntegrationTest {
        private ExtensibleLockProvider lockProvider;

        private JedisCluster jedisCluster;

        @BeforeEach
        public void createLockProvider() {
            jedisCluster = new JedisCluster(new HostAndPort(redis.getContainerIpAddress(), redis.getFirstMappedPort()));
            lockProvider = new JedisLockProvider(jedisCluster, ENV);
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
            return jedisCluster.get(JedisLockProvider.buildKey(lockName, ENV));
        }

        @Override
        protected ExtensibleLockProvider getLockProvider() {
            return lockProvider;
        }
    }

    @Nested
    class Pool extends AbstractLockProviderIntegrationTest {
        private LockProvider lockProvider;

        private JedisPool jedisPool;

        @BeforeEach
        public void createLockProvider() {
            jedisPool = new JedisPool(redis.getContainerIpAddress(), redis.getMappedPort(PORT));
            lockProvider = new JedisLockProvider(jedisPool, ENV);
        }

        @Override
        protected void assertUnlocked(String lockName) {
            try (Jedis jedis = jedisPool.getResource()) {
                assertThat(getLock(lockName, jedis)).isNull();
            }
        }

        @Override
        protected void assertLocked(String lockName) {
            try (Jedis jedis = jedisPool.getResource()) {
                assertThat(getLock(lockName, jedis)).isNotNull();
            }
        }

        private String getLock(String lockName, Jedis jedis) {
            return jedis.get(JedisLockProvider.buildKey(lockName, ENV));
        }

        @Override
        protected LockProvider getLockProvider() {
            return lockProvider;
        }
    }
}
