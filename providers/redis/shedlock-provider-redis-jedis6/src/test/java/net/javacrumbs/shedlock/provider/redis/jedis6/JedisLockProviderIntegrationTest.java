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
package net.javacrumbs.shedlock.provider.redis.jedis6;

import static net.javacrumbs.shedlock.provider.redis.testsupport.RedisContainer.ENV;
import static net.javacrumbs.shedlock.provider.redis.testsupport.RedisContainer.PORT;

import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.provider.redis.testsupport.AbstractRedisIntegrationTest;
import net.javacrumbs.shedlock.provider.redis.testsupport.AbstractRedisSafeUpdateIntegrationTest;
import net.javacrumbs.shedlock.provider.redis.testsupport.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;

@Testcontainers
public class JedisLockProviderIntegrationTest {

    @Container
    public static final RedisContainer redis = new RedisContainer(PORT);

    @Nested
    class Cluster extends AbstractRedisIntegrationTest {
        private ExtensibleLockProvider lockProvider;

        private JedisCluster jedisCluster;

        @BeforeEach
        public void createLockProvider() {
            jedisCluster = new JedisCluster(new HostAndPort(redis.getHost(), redis.getFirstMappedPort()));
            lockProvider = new JedisLockProvider(jedisCluster, ENV);
        }

        @Override
        protected String getLock(String lockName) {
            return jedisCluster.get(buildKey(lockName, ENV));
        }

        @Override
        protected ExtensibleLockProvider getLockProvider() {
            return lockProvider;
        }
    }

    @Nested
    class ClusterSafeUpdate extends AbstractRedisSafeUpdateIntegrationTest {
        private ExtensibleLockProvider lockProvider;

        private JedisCluster jedisCluster;

        @BeforeEach
        public void createLockProvider() {
            jedisCluster = new JedisCluster(new HostAndPort(redis.getHost(), redis.getFirstMappedPort()));
            lockProvider = new JedisLockProvider(jedisCluster, ENV, true);
        }

        @Override
        protected String getLock(String lockName) {
            return jedisCluster.get(buildKey(lockName, ENV));
        }

        @Override
        protected ExtensibleLockProvider getLockProvider() {
            return lockProvider;
        }
    }

    @Nested
    class Pool extends AbstractRedisIntegrationTest {
        private ExtensibleLockProvider lockProvider;

        private JedisPool jedisPool;

        @BeforeEach
        public void createLockProvider() {
            jedisPool = new JedisPool(redis.getHost(), redis.getMappedPort(PORT));
            lockProvider = new JedisLockProvider(jedisPool, ENV);
        }

        @Override
        protected String getLock(String lockName) {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.get(buildKey(lockName, ENV));
            }
        }

        @Override
        protected ExtensibleLockProvider getLockProvider() {
            return lockProvider;
        }
    }

    @Nested
    class PoolSafeUpdate extends AbstractRedisSafeUpdateIntegrationTest {
        private ExtensibleLockProvider lockProvider;

        private JedisPool jedisPool;

        @BeforeEach
        public void createLockProvider() {
            jedisPool = new JedisPool(redis.getHost(), redis.getMappedPort(PORT));
            lockProvider = new JedisLockProvider(jedisPool, ENV, true);
        }

        @Override
        protected String getLock(String lockName) {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.get(buildKey(lockName, ENV));
            }
        }

        @Override
        protected ExtensibleLockProvider getLockProvider() {
            return lockProvider;
        }
    }
}
