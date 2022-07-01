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
package net.javacrumbs.shedlock.provider.redis.spring;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import redis.embedded.RedisServer;

import java.io.IOException;

public class SpringRedisLockProviderIntegrationTest {
    private static RedisServer redisServer;

    private final static int PORT = 6381;
    protected final static String HOST = "localhost";


    @BeforeAll
    public static void startRedis() throws IOException {
        redisServer = new RedisServer(PORT);
        redisServer.start();
    }

    @AfterAll
    public static void stopRedis() {
        redisServer.stop();
    }

    @Nested
    class Jedis extends AbstractRedisLockProviderIntegrationTest {
        public Jedis() {
            super(createJedisConnectionFactory());
        }
    }

    private static RedisConnectionFactory createJedisConnectionFactory() {
        JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory();
        jedisConnectionFactory.setHostName(HOST);
        jedisConnectionFactory.setPort(PORT);
        jedisConnectionFactory.afterPropertiesSet();
        return jedisConnectionFactory;
    }

    @Nested
    class Letucce extends AbstractRedisLockProviderIntegrationTest {
        public Letucce() {
            super(createLettuceConnectionFactory());
        }
    }

    @Nested
    class ReactiveLetucce extends AbstractReactiveRedisLockProviderIntegrationTest {
        public ReactiveLetucce() {
            super(createLettuceConnectionFactory());
        }
    }

    private static LettuceConnectionFactory createLettuceConnectionFactory() {
        LettuceConnectionFactory lettuceConnectionFactory = new LettuceConnectionFactory(HOST, PORT);
        lettuceConnectionFactory.afterPropertiesSet();
        return lettuceConnectionFactory;
    }

    @Nested
    class Redisson extends AbstractRedisLockProviderIntegrationTest {
        public Redisson() {
            super(createRedissonConnectionFactory());
        }
    }

    private static RedisConnectionFactory createRedissonConnectionFactory() {
        Config config = new Config();
        config.useSingleServer()
            .setAddress("redis://" + HOST + ":" + PORT);
        RedissonClient redisson = org.redisson.Redisson.create(config);
        return new RedissonConnectionFactory(redisson);
    }
}
