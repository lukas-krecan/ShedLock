package net.javacrumbs.shedlock.provider.redis.spring;

import static java.lang.Boolean.TRUE;
import static net.javacrumbs.shedlock.provider.redis.support.InternalRedisLockProvider.DEFAULT_KEY_PREFIX;
import static net.javacrumbs.shedlock.provider.redis.support.InternalRedisLockProvider.ENV_DEFAULT;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.redis.support.InternalRedisLockProvider;
import net.javacrumbs.shedlock.provider.redis.support.InternalRedisLockTemplate;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Uses Redis's `SET resource-name anystring NX PX max-lock-ms-time` as locking
 * mechanism. See https://redis.io/commands/set
 */
public class ReactiveRedisLockProvider implements LockProvider {
    private final InternalRedisLockProvider internalRedisLockProvider;

    public ReactiveRedisLockProvider(@NonNull ReactiveRedisConnectionFactory redisConn) {
        this(redisConn, ENV_DEFAULT);
    }

    /**
     * Creates ReactiveRedisLockProvider
     *
     * @param redisConn
     *            ReactiveRedisConnectionFactory
     * @param environment
     *            environment is part of the key and thus makes sure there is no
     *            key conflict between multiple ShedLock instances running on the
     *            same Redis
     */
    public ReactiveRedisLockProvider(@NonNull ReactiveRedisConnectionFactory redisConn, @NonNull String environment) {
        this(redisConn, environment, DEFAULT_KEY_PREFIX);
    }

    /**
     * Creates ReactiveRedisLockProvider
     *
     * @param redisConn
     *            ReactiveRedisConnectionFactory
     * @param environment
     *            environment is part of the key and thus makes sure there is no
     *            key conflict between multiple ShedLock instances running on the
     *            same Redis
     * @param keyPrefix
     *            prefix of the key in Redis.
     */
    public ReactiveRedisLockProvider(
            @NonNull ReactiveRedisConnectionFactory redisConn, @NonNull String environment, @NonNull String keyPrefix) {
        this(new ReactiveStringRedisTemplate(redisConn), environment, keyPrefix);
    }

    /**
     * Create ReactiveRedisLockProvider
     *
     * @param redisTemplate
     *            ReactiveStringRedisTemplate
     * @param environment
     *            environment is part of the key and thus makes sure there is no
     *            key conflict between multiple ShedLock instances running on the
     *            same Redis
     * @param keyPrefix
     *            prefix of the key in Redis.
     */
    public ReactiveRedisLockProvider(
            @NonNull ReactiveStringRedisTemplate redisTemplate,
            @NonNull String environment,
            @NonNull String keyPrefix) {
        this.internalRedisLockProvider = new InternalRedisLockProvider(
                new ReactiveRedisLockTemplate(redisTemplate), environment, keyPrefix, false);
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        return internalRedisLockProvider.lock(lockConfiguration);
    }

    public static class Builder {
        private final ReactiveStringRedisTemplate redisTemplate;
        private String environment = ENV_DEFAULT;
        private String keyPrefix = DEFAULT_KEY_PREFIX;

        public Builder(@NonNull ReactiveRedisConnectionFactory redisConnectionFactory) {
            this.redisTemplate = new ReactiveStringRedisTemplate(redisConnectionFactory);
        }

        public Builder(@NonNull ReactiveStringRedisTemplate redisTemplate) {
            this.redisTemplate = redisTemplate;
        }

        public ReactiveRedisLockProvider.Builder environment(@NonNull String environment) {
            this.environment = environment;
            return this;
        }

        public ReactiveRedisLockProvider.Builder keyPrefix(@NonNull String keyPrefix) {
            this.keyPrefix = keyPrefix;
            return this;
        }

        public ReactiveRedisLockProvider build() {
            return new ReactiveRedisLockProvider(redisTemplate, environment, keyPrefix);
        }
    }

    private record ReactiveRedisLockTemplate(ReactiveStringRedisTemplate redisTemplate)
            implements InternalRedisLockTemplate {

        @Override
        public boolean setIfAbsent(String key, String value, long expirationMs) {
            return TRUE
                    == redisTemplate
                            .opsForValue()
                            .setIfAbsent(key, value, Duration.ofMillis(expirationMs))
                            .block();
        }

        @Override
        public boolean setIfPresent(String key, String value, long expirationMs) {
            return TRUE
                    == redisTemplate
                            .opsForValue()
                            .setIfPresent(key, value, Duration.ofMillis(expirationMs))
                            .block();
        }

        @Override
        public Object eval(String script, String key, String... values) {
            return redisTemplate
                    .execute(new DefaultRedisScript<>(script, Integer.class), List.of(key), List.of(values))
                    .next()
                    .block();
        }

        @Override
        public void delete(String key) {
            redisTemplate.delete(key).block();
        }
    }
}
