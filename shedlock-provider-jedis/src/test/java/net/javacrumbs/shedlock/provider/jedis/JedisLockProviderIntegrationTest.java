/**
 * Copyright 2009-2017 the original author or authors.
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
package net.javacrumbs.shedlock.provider.jedis;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.Assert;
import org.junit.Before;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.util.Optional;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;

public class JedisLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    private static JedisPool jedisPool;
    private LockProvider lockProvider;

    private final static int PORT = 6379;

    @Before
    public void createLockProvider() {
        jedisPool = new JedisPool(new JedisPoolConfig(), "localhost");
        lockProvider = new JedisLockProvider(jedisPool, "test");
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    @Override
    protected void assertUnlocked(String lockName) {
        try (Jedis jedis = jedisPool.getResource()) {
            Assert.assertNull(jedis.get(JedisLockProvider.buildKey(lockName)));
        }
    }

    @Override
    protected void assertLocked(String lockName) {
        try (Jedis jedis = jedisPool.getResource()) {
            Assert.assertNotNull(jedis.get(JedisLockProvider.buildKey(lockName)));
        }
    }

    @Override
    public void shouldTimeout() throws InterruptedException {
        LockConfiguration configWithShortTimeout = lockConfig(LOCK_NAME1, 2, Duration.ZERO);
        Optional<SimpleLock> lock1 = getLockProvider().lock(configWithShortTimeout);
        assertThat(lock1).isNotEmpty();

        sleep(5);

        assertUnlocked(configWithShortTimeout.getName());
    }

    @Override
    public void shouldLockAtLeastFor() throws InterruptedException {
        LockConfiguration configWithGracePeriod = lockConfig(LOCK_NAME1, 0, LOCK_AT_LEAST_FOR);
        Optional<SimpleLock> lock1 = getLockProvider().lock(configWithGracePeriod);
        assertThat(lock1).isNotEmpty();

        // can not acquire lock, grace period did not pass yet
        configWithGracePeriod = lockConfig(LOCK_NAME1, 0, LOCK_AT_LEAST_FOR);
        Optional<SimpleLock> lock2 = getLockProvider().lock(configWithGracePeriod);
        assertThat(lock2).isEmpty();

        sleep(LOCK_AT_LEAST_FOR.toMillis());
        configWithGracePeriod = lockConfig(LOCK_NAME1, 0, LOCK_AT_LEAST_FOR);
        Optional<SimpleLock> lock3 = getLockProvider().lock(configWithGracePeriod);
        assertThat(lock3).isNotEmpty();
        lock3.get().unlock();
    }
}