package net.javacrumbs.shedlock.provider.redis.spring;

import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static net.javacrumbs.shedlock.support.Utils.getHostname;
import static net.javacrumbs.shedlock.support.Utils.toIsoString;

/**
 * Uses Redis's `SET resource-name anystring NX PX max-lock-ms-time` as locking mechanism.
 * See https://redis.io/commands/set
 */
public class ReactiveRedisLockProvider implements LockProvider {
    private static final String KEY_PREFIX_DEFAULT = "job-lock";
    private static final String ENV_DEFAULT = "default";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final String environment;
    private final String keyPrefix;

    public ReactiveRedisLockProvider(@NonNull ReactiveRedisConnectionFactory redisConn) {
        this(redisConn, ENV_DEFAULT);
    }

    /**
     * Creates ReactiveRedisLockProvider
     *
     * @param redisConn   ReactiveRedisConnectionFactory
     * @param environment environment is part of the key and thus makes sure there is not key conflict between
     *                    multiple ShedLock instances running on the same Redis
     */
    public ReactiveRedisLockProvider(@NonNull ReactiveRedisConnectionFactory redisConn, @NonNull String environment) {
        this(redisConn, environment, KEY_PREFIX_DEFAULT);
    }

    /**
     * Creates ReactiveRedisLockProvider
     *
     * @param redisConn   ReactiveRedisConnectionFactory
     * @param environment environment is part of the key and thus makes sure there is not key conflict between
     *                    multiple ShedLock instances running on the same Redis
     * @param keyPrefix   prefix of the key in Redis.
     */
    public ReactiveRedisLockProvider(@NonNull ReactiveRedisConnectionFactory redisConn, @NonNull String environment, @NonNull String keyPrefix) {
        this(new ReactiveStringRedisTemplate(redisConn), environment, keyPrefix);
    }

    /**
     * Create ReactiveRedisLockProvider
     *
     * @param redisTemplate ReactiveStringRedisTemplate
     * @param environment   environment is part of the key and thus makes sure there is not key conflict between
     *                      multiple ShedLock instances running on the same Redis
     * @param keyPrefix     prefix of the key in Redis.
     */
    public ReactiveRedisLockProvider(@NonNull ReactiveStringRedisTemplate redisTemplate, @NonNull String environment, @NonNull String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.environment = environment;
        this.keyPrefix = keyPrefix;
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        Instant now = ClockProvider.now();
        String key = ReactiveRedisLock.createKey(keyPrefix, environment, lockConfiguration.getName());
        String value = ReactiveRedisLock.createValue(now);
        Duration expirationTime = Duration.between(now, lockConfiguration.getLockAtMostUntil());
        Boolean lockResult = redisTemplate.opsForValue().setIfAbsent(key, value, expirationTime).block();
        if (Boolean.TRUE.equals(lockResult)) {
            return Optional.of(new ReactiveRedisLock(key, redisTemplate, lockConfiguration));
        }
        return Optional.empty();
    }

    private static final class ReactiveRedisLock extends AbstractSimpleLock {
        private final String key;
        private final ReactiveStringRedisTemplate redisTemplate;

        private static String createKey(String keyPrefix, String environment, String lockName) {
            return String.format("%s:%s:%s", keyPrefix, environment, lockName);
        }

        private static String createValue(Instant now) {
            return String.format("ADDED:%s@%s", toIsoString(now), getHostname());
        }

        private ReactiveRedisLock(String key, ReactiveStringRedisTemplate redisTemplate, LockConfiguration lockConfiguration) {
            super(lockConfiguration);
            this.key = key;
            this.redisTemplate = redisTemplate;
        }

        @Override
        protected void doUnlock() {
            Instant now = ClockProvider.now();
            Duration expirationTime = Duration.between(now, lockConfiguration.getLockAtLeastUntil());
            if (expirationTime.isNegative() || expirationTime.isZero()) {
                try {
                    redisTemplate.delete(key).block();
                } catch (Exception e) {
                    throw new LockException("Can not remove node", e);
                }
            } else {
                String value = createValue(now);
                redisTemplate.opsForValue().setIfPresent(key, value, expirationTime).block();
            }
        }
    }

    public static class Builder {
        private final ReactiveStringRedisTemplate redisTemplate;
        private String environment = ENV_DEFAULT;
        private String keyPrefix = KEY_PREFIX_DEFAULT;

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
}
