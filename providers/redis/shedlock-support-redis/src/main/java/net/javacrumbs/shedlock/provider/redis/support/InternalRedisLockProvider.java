/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.redis.support;

import static net.javacrumbs.shedlock.support.Utils.getHostname;
import static net.javacrumbs.shedlock.support.Utils.toIsoString;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;

/**
 * Common implementation of RedisLockProvider. Internal class, please don't use directly.
 */
public class InternalRedisLockProvider implements ExtensibleLockProvider {

    public static final String DEFAULT_KEY_PREFIX = "job-lock";
    public static final String ENV_DEFAULT = "default";
    private static final Long ONE = 1L;

    private final InternalRedisLockTemplate redisLockTemplate;
    private final String environment;
    private final String keyPrefix;
    private final boolean safeUpdate;

    /*
     * https://redis.io/docs/latest/develop/use/patterns/distributed-locks/
     * */
    private static final String delLuaScript =
            """
        if redis.call("get",KEYS[1]) == ARGV[1] then
            return redis.call("del",KEYS[1])
        else
            return 0
        end
        """;

    private static final String updLuaScript =
            """
        if redis.call('get', KEYS[1]) == ARGV[1] then
           return redis.call('pexpire', KEYS[1], ARGV[2])
        else
           return 0
        end
        """;

    public InternalRedisLockProvider(
            InternalRedisLockTemplate redisLockTemplate, String environment, String keyPrefix, boolean safeUpdate) {
        this.redisLockTemplate = redisLockTemplate;
        this.environment = environment;
        this.keyPrefix = keyPrefix;
        this.safeUpdate = safeUpdate;
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        long expireTime = getMsUntil(lockConfiguration.getLockAtMostUntil());

        String key = buildKey(lockConfiguration.getName(), keyPrefix, this.environment);
        String uniqueLockValue = buildValue();

        if (createLock(key, uniqueLockValue, expireTime)) {
            return Optional.of(new RedisLock(key, uniqueLockValue, this, lockConfiguration));
        }

        return Optional.empty();
    }

    private Optional<SimpleLock> extend(RedisLock currentLock, LockConfiguration lockConfiguration) {
        long expireTime = getMsUntil(lockConfiguration.getLockAtMostUntil());

        if (setKeyExpiration(currentLock, expireTime)) {
            return Optional.of(new RedisLock(currentLock.key, currentLock.value, this, lockConfiguration));
        }

        return Optional.empty();
    }

    private boolean setKeyExpiration(RedisLock currentLock, long expiration) {
        if (safeUpdate) {
            return ONE.equals(redisLockTemplate.eval(
                    updLuaScript, currentLock.key, currentLock.value, String.valueOf(expiration)));
        } else {
            return redisLockTemplate.setIfPresent(currentLock.key, currentLock.value, expiration);
        }
    }

    private boolean createLock(String key, String value, long expirationMs) {
        return redisLockTemplate.setIfAbsent(key, value, expirationMs);
    }

    private void deleteLock(String key, String value) {
        if (safeUpdate) {
            redisLockTemplate.eval(delLuaScript, key, value);
        } else {
            redisLockTemplate.delete(key);
        }
    }

    private static final class RedisLock extends AbstractSimpleLock {
        private final String key;
        private final String value;
        private final InternalRedisLockProvider lockProvider;

        private RedisLock(
                String key, String value, InternalRedisLockProvider lockProvider, LockConfiguration lockConfiguration) {
            super(lockConfiguration);
            this.key = key;
            this.value = value;
            this.lockProvider = lockProvider;
        }

        @Override
        public void doUnlock() {
            long keepLockFor = getMsUntil(lockConfiguration.getLockAtLeastUntil());

            // lock at least until is in the past
            if (keepLockFor <= 0) {
                try {
                    lockProvider.deleteLock(key, value);
                } catch (Exception e) {
                    throw new LockException("Can not remove node", e);
                }
            } else {
                lockProvider.setKeyExpiration(this, keepLockFor);
            }
        }

        @Override
        protected Optional<SimpleLock> doExtend(LockConfiguration newConfiguration) {
            return lockProvider.extend(this, newConfiguration);
        }
    }

    private static long getMsUntil(Instant instant) {
        return Duration.between(ClockProvider.now(), instant).toMillis();
    }

    private static String buildKey(String lockName, String keyPrefix, String env) {
        return String.format("%s:%s:%s", keyPrefix, env, lockName);
    }

    private static String buildValue() {
        return String.format("ADDED:%s@%s:%s", toIsoString(ClockProvider.now()), getHostname(), UUID.randomUUID());
    }
}
