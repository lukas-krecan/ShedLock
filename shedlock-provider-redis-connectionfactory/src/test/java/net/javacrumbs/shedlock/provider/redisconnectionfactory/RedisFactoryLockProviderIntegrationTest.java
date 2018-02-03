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
package net.javacrumbs.shedlock.provider.redisconnectionfactory;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import static java.lang.Thread.sleep;
import static net.javacrumbs.shedlock.provider.redisconnectionfactory.RedisFactoryLockProvider.buildKey;
import static org.assertj.core.api.Assertions.assertThat;

public class RedisFactoryLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    private static JedisConnectionFactory jedisConnectionFactory;
    private static RedisServer redisServer;
    private LockProvider lockProvider;
    private StringRedisTemplate redisTemplate;

    private final static int PORT = 6380;
    private final static String HOST = "localhost";
    private final static String ENV = "test";

    @BeforeClass
    public static void startRedis() throws IOException {
        redisServer = new RedisServer(PORT);
        redisServer.start();
    }

    @AfterClass
    public static void stopRedis() {
        redisServer.stop();
    }

    @Before
    public void createLockProvider() {
        jedisConnectionFactory = new JedisConnectionFactory(new RedisStandaloneConfiguration(HOST, PORT));
        lockProvider = new RedisFactoryLockProvider(jedisConnectionFactory, ENV);
        redisTemplate = new StringRedisTemplate(jedisConnectionFactory);
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    @Override
    protected void assertUnlocked(String lockName) {
        assertThat(redisTemplate.hasKey(buildKey(lockName, ENV))).isFalse();
    }

    @Override
    protected void assertLocked(String lockName) {
        assertThat(redisTemplate.getExpire(buildKey(lockName, ENV))).isGreaterThan(0);
    }

    @Override
    public void shouldTimeout() throws InterruptedException {
        LockConfiguration configWithShortTimeout = lockConfig(LOCK_NAME1, Duration.ofMillis(2), Duration.ZERO);
        Optional<SimpleLock> lock1 = getLockProvider().lock(configWithShortTimeout);
        assertThat(lock1).isNotEmpty();

        sleep(5);

        // Get new config with updated timeout
        configWithShortTimeout = lockConfig(LOCK_NAME1, Duration.ofMillis(2), Duration.ZERO);
        assertUnlocked(configWithShortTimeout.getName());
    }
}