package net.javacrumbs.shedlock.provider.redis.spring;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractReactiveRedisLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {
    private final ReactiveRedisLockProvider lockProvider;
    private final ReactiveStringRedisTemplate redisTemplate;

    private final static String ENV = "test";
    private final static String KEY_PREFIX = "test-prefix";

    public AbstractReactiveRedisLockProviderIntegrationTest(ReactiveRedisConnectionFactory connectionFactory) {
        lockProvider = new ReactiveRedisLockProvider.Builder(connectionFactory)
            .environment(ENV)
            .keyPrefix(KEY_PREFIX)
            .build();

        redisTemplate = new ReactiveStringRedisTemplate(connectionFactory);
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    @Override
    protected void assertUnlocked(String lockName) {
        assertThat(redisTemplate.hasKey(createKey(lockName)).block()).isFalse();
    }

    @Override
    protected void assertLocked(String lockName) {
        assertThat(redisTemplate.getExpire(createKey(lockName)).block()).isPositive();
    }

    private String createKey(String lockName) {
        return KEY_PREFIX + ":" + ENV + ":" + lockName;
    }
}
