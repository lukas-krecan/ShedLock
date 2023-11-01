package net.javacrumbs.shedlock.provider.redis.quarkus;

import static io.vertx.mutiny.redis.client.Command.SET;
import static net.javacrumbs.shedlock.support.Utils.getHostname;
import static net.javacrumbs.shedlock.support.Utils.toIsoString;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.vertx.mutiny.redis.client.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;

/**
 * Uses Redis's `SET resource-name anystring NX EX max-lock-ms-time` as locking
 * mechanism.
 *
 * <p>
 * See <a href="https://redis.io/commands/set">Set command</a>
 */
public class QuarkusRedisLockProvider implements ExtensibleLockProvider {

    private static final String KEY_PREFIX_DEFAULT = "job-lock";

    private final RedisDataSource redisDataSource;
    private final KeyCommands<String> keyCommands;

    private final String keyPrefix;

    public QuarkusRedisLockProvider(RedisDataSource dataSource) {
        this(dataSource, KEY_PREFIX_DEFAULT);
    }

    public QuarkusRedisLockProvider(@NonNull RedisDataSource dataSource, @NonNull String keyPrefix) {
        this.keyPrefix = keyPrefix;
        this.redisDataSource = dataSource;
        this.keyCommands = dataSource.key(String.class);
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {

        long expireTime = getMillisUntil(lockConfiguration.getLockAtMostUntil());

        String key = buildKey(lockConfiguration.getName());

        Response response = redisDataSource.execute(SET, key, buildValue(), "NX", "PX", Long.toString(expireTime));
        if (response != null && "OK".equals(response.toString())) {
            return Optional.of(new RedisLock(key, this, lockConfiguration));
        } else {
            return Optional.empty();
        }
    }

    private Optional<SimpleLock> extend(LockConfiguration lockConfiguration) {

        long expireTime = getMillisUntil(lockConfiguration.getLockAtMostUntil());

        String key = buildKey(lockConfiguration.getName());

        boolean success = extendKeyExpiration(key, expireTime);

        if (success) {
            return Optional.of(new RedisLock(key, this, lockConfiguration));
        }

        return Optional.empty();
    }

    String buildKey(String lockName) {
        return keyPrefix + ":" + lockName;
    }

    private boolean extendKeyExpiration(String key, long expiration) {
        return keyCommands.pexpire(key, expiration);
    }

    private void deleteKey(String key) {
        keyCommands.del(key);
    }

    private static final class RedisLock extends AbstractSimpleLock {
        private final String key;
        private final QuarkusRedisLockProvider quarkusLockProvider;

        private RedisLock(String key, QuarkusRedisLockProvider jedisLockProvider, LockConfiguration lockConfiguration) {
            super(lockConfiguration);
            this.key = key;
            this.quarkusLockProvider = jedisLockProvider;
        }

        @Override
        public void doUnlock() {
            long keepLockFor = getMillisUntil(lockConfiguration.getLockAtLeastUntil());

            // lock at least until is in the past
            if (keepLockFor <= 0) {
                try {
                    quarkusLockProvider.deleteKey(key);
                } catch (Exception e) {
                    throw new LockException("Can not remove key", e);
                }
            } else {
                quarkusLockProvider.extendKeyExpiration(key, keepLockFor);
            }
        }

        @Override
        @NonNull
        protected Optional<SimpleLock> doExtend(@NonNull LockConfiguration newConfiguration) {
            return quarkusLockProvider.extend(newConfiguration);
        }
    }

    private static long getMillisUntil(Instant instant) {
        return Duration.between(ClockProvider.now(), instant).toMillis();
    }

    private static String buildValue() {
        return String.format("ADDED:%s@%s", toIsoString(ClockProvider.now()), getHostname());
    }
}
