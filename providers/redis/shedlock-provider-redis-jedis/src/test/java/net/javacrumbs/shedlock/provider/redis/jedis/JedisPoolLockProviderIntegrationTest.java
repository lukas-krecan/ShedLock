/**
 * Copyright 2009-2020 the original author or authors.
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

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import static net.javacrumbs.shedlock.provider.redis.jedis.RedisContainer.ENV;
import static net.javacrumbs.shedlock.provider.redis.jedis.RedisContainer.PORT;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@Testcontainers
public class JedisPoolLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    private LockProvider lockProvider;

    private static JedisPool jedisPool;

    private final static int HOST_PORT = 6380;

    @Container
    public static final RedisContainer redis = new RedisContainer(HOST_PORT);

    @BeforeEach
    public void createLockProvider() {
        jedisPool = new JedisPool(redis.getContainerIpAddress(), redis.getMappedPort(PORT));
        lockProvider = new JedisLockProvider(jedisPool, ENV);
    }

    @Override
    protected void assertUnlocked(String lockName) {
        try (Jedis jedis = jedisPool.getResource()) {
            assertNull(jedis.get(JedisLockProvider.buildKey(lockName, ENV)));
        }
    }

    @Override
    protected void assertLocked(String lockName) {
        try (Jedis jedis = jedisPool.getResource()) {
            assertNotNull(jedis.get(JedisLockProvider.buildKey(lockName, ENV)));
        }
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }
}
