package net.javacrumbs.shedlock.provider.redis.spring;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;

public class RedissonLockProviderIntegrationTest extends AbstractRedisLockProviderIntegrationTest {
    public RedissonLockProviderIntegrationTest() {
        super(createRedissonConnectionFactory());
    }


    private static RedisConnectionFactory createRedissonConnectionFactory() {
        Config config = new Config();
        config.useSingleServer()
            .setAddress("redis://" + HOST + ":" + PORT);
        RedissonClient redisson = Redisson.create(config);
        return new RedissonConnectionFactory(redisson);
    }
}
