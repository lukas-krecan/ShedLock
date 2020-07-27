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

    private static RedisConnectionFactory createLettuceConnectionFactory() {
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
