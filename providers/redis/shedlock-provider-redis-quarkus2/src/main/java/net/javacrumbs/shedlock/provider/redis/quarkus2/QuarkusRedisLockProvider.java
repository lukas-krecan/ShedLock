package net.javacrumbs.shedlock.provider.redis.quarkus2;

import static net.javacrumbs.shedlock.support.Utils.getHostname;
import static net.javacrumbs.shedlock.support.Utils.toIsoString;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.runtime.configuration.ConfigUtils;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;

/**
 * Uses Redis's `SET resource-name anystring NX EX max-lock-ms-time` as locking mechanism.
 * <p>
 * See <a href="https://redis.io/commands/set">Set command</a>
 */
public class QuarkusRedisLockProvider implements ExtensibleLockProvider {
    
    private static final Logger LOG = LoggerFactory.getLogger(QuarkusRedisLockProvider.class);

    private static final String KEY_PREFIX = "lock";

    private final RedisDataSource redisDataSource;
    private final ValueCommands<String, String> valueCommands;
    private final KeyCommands<String> keyCommands;
    private final String environment;

    private boolean throwsException;

    public QuarkusRedisLockProvider(RedisDataSource dataSource, String appNameOrPrefix, boolean throwsException) {
        this.redisDataSource = dataSource;
        this.throwsException = throwsException;
        this.environment = appNameOrPrefix + ":"+ String.join(":", ConfigUtils.getProfiles());
        this.valueCommands = redisDataSource.value(String.class);
        this.keyCommands = redisDataSource.key(String.class);
    }

   
    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
       
        long expireTime = getMillisUntil(lockConfiguration.getLockAtMostUntil());

        String key = buildKey(lockConfiguration.getName(), this.environment);
        
        String value = valueCommands.setGet(key, buildValue(),  new SetArgs().nx().px(expireTime));
        if(value != null) {
            if(throwsException) throw new LockException("Already locked !");
            return Optional.empty();
        }else {
            return Optional.of(new RedisLock(key, this, lockConfiguration));
        }
        
    }

    private Optional<SimpleLock> extend(LockConfiguration lockConfiguration) {
        
        long expireTime = getMillisUntil(lockConfiguration.getLockAtMostUntil());

        String key = buildKey(lockConfiguration.getName(), this.environment);

        boolean success = extendKeyExpiration(key, expireTime);

        if (success) {
            return Optional.of(new RedisLock(key, this, lockConfiguration));
        }

        return Optional.empty();
    }


    private boolean extendKeyExpiration(String key, long expiration) {
        String value = valueCommands.setGet(key, buildValue(),  new SetArgs().xx().px(expiration));
        return value != null;
        
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

    static String buildKey(String lockName, String env) {
        return String.format("%s:%s:%s", KEY_PREFIX, env, lockName);
    }

    private static String buildValue() {
        return String.format("ADDED:%s@%s", toIsoString(ClockProvider.now()), getHostname());
    }

   

}