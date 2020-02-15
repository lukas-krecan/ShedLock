package net.javacrumbs.shedlock.provider.redis.spring1;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

public class LetucceLockProviderIntegrationTest extends AbstractRedisLockProviderIntegrationTest {
    public LetucceLockProviderIntegrationTest() {
        super(createLettuceConnectionFactory());
    }

    private static RedisConnectionFactory createLettuceConnectionFactory() {
        LettuceConnectionFactory lettuceConnectionFactory = new LettuceConnectionFactory(HOST, PORT);
        lettuceConnectionFactory.afterPropertiesSet();
        return lettuceConnectionFactory;
    }
}
