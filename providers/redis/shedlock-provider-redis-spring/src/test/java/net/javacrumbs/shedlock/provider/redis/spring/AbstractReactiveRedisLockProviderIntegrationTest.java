package net.javacrumbs.shedlock.provider.redis.spring;

import static org.assertj.core.api.Assertions.assertThat;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

public abstract class AbstractReactiveRedisLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {
    private final ReactiveRedisLockProvider lockProvider;
    private final ReactiveStringRedisTemplate redisTemplate;

    private static final String ENV = "test";
    private static final String KEY_PREFIX = "test-prefix";

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
