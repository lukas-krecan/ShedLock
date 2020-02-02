package net.javacrumbs.shedlock.provider.redis.spring;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;

public class JedisLockProviderIntegrationTest extends AbstractRedisLockProviderIntegrationTest {
    public JedisLockProviderIntegrationTest() {
        super(createJedisConnectionFactory());
    }

    private static RedisConnectionFactory createJedisConnectionFactory() {
        JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory();
        jedisConnectionFactory.setHostName(HOST);
        jedisConnectionFactory.setPort(PORT);
        jedisConnectionFactory.afterPropertiesSet();
        return jedisConnectionFactory;
    }
}
