/**
 * Copyright 2009-2018 the original author or authors.
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
package net.javacrumbs.shedlock.provider.redis.spring;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider.buildKey;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class RedisLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    private static RedisServer redisServer;
    private LockProvider lockProvider;
    private StringRedisTemplate redisTemplate;

    final static int PORT = 6380;
    final static String HOST = "localhost";
    private final static String ENV = "test";

    @Parameters
    public static Collection<RedisConnectionFactory> data() {
        return Arrays.asList(createJedisConnectionFactory(), createLettuceConnectionFactory());
    }

    @BeforeClass
    public static void startRedis() throws IOException {
        redisServer = new RedisServer(PORT);
        redisServer.start();
    }

    @AfterClass
    public static void stopRedis() {
        redisServer.stop();
    }

    public RedisLockProviderIntegrationTest(RedisConnectionFactory connectionFactory) {
        lockProvider = new RedisLockProvider(connectionFactory, ENV);
        redisTemplate = new StringRedisTemplate(connectionFactory);
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


    private static RedisConnectionFactory createJedisConnectionFactory() {
        JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory();
        jedisConnectionFactory.setHostName(HOST);
        jedisConnectionFactory.setPort(PORT);
        jedisConnectionFactory.afterPropertiesSet();
        return jedisConnectionFactory;
    }

    private static RedisConnectionFactory createLettuceConnectionFactory() {
        LettuceConnectionFactory lettuceConnectionFactory = new LettuceConnectionFactory(HOST, PORT);
        lettuceConnectionFactory.afterPropertiesSet();
        return lettuceConnectionFactory;
    }
}