/**
 * Copyright 2009-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.redis.spring1;

import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.types.Expiration;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static net.javacrumbs.shedlock.support.Utils.getHostname;

/**
 * Uses Redis's `SETNX resource-name anystring` as locking mechanism.
 * See https://redis.io/commands/setnx
 *
 * @deprecated Support for Spring Data Redis 1, for Spring Data Redis 2 use different module
 */
@Deprecated
public class RedisLockProvider implements LockProvider {
    private static final String KEY_PREFIX_DEFAULT = "job-lock";
    private static final String ENV_DEFAULT = "default";
    private final RedisConnectionFactory redisConnectionFactory;
    private final String environment;
    private final String keyPrefix;

    public RedisLockProvider(@NonNull RedisConnectionFactory redisConn) {
        this(redisConn, ENV_DEFAULT);
    }

    public RedisLockProvider(@NonNull RedisConnectionFactory redisConn, @NonNull String environment) {
        this(redisConn, environment, KEY_PREFIX_DEFAULT);
    }

    public RedisLockProvider(@NonNull RedisConnectionFactory redisConn, @NonNull String environment, @NonNull String keyPrefix) {
        this.redisConnectionFactory = redisConn;
        this.environment = environment;
        this.keyPrefix = keyPrefix;
    }

    // See https://redis.io/commands/setnx#handling-deadlocks
    @NonNull
    @Override
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        String key = buildKey(lockConfiguration.getName());
        RedisConnection redisConnection = null;
        try {
            byte[] keyBytes = key.getBytes();
            redisConnection = redisConnectionFactory.getConnection();
            if (redisConnection.setNX(keyBytes, buildValue(lockConfiguration.getLockAtMostUntil()))) {
                return Optional.of(new RedisLock(key, redisConnectionFactory, lockConfiguration));
            } else {
                byte[] value = redisConnection.get(keyBytes);
                if(isUnlocked(value)) {
                    byte[] maybeOldValue = redisConnection.getSet(keyBytes, buildValue(lockConfiguration.getLockAtMostUntil()));
                    if(isUnlocked(maybeOldValue)) {
                        return Optional.of(new RedisLock(key, redisConnectionFactory, lockConfiguration));
                    }
                }
                return Optional.empty();
            }
        } finally {
            close(redisConnection);
        }
    }

    private boolean isUnlocked(byte[] value) {
        return value == null || getExpirationFromValue(value).getExpirationTimeInMilliseconds() <= 0;
    }

    private static Expiration getExpiration(Instant until) {
        return Expiration.from(getMsUntil(until), TimeUnit.MILLISECONDS);
    }

    private static long getMsUntil(Instant until) {
        return Duration.between(ClockProvider.now(), until).toMillis();
    }

    private static void close(RedisConnection redisConnection) {
        if (redisConnection != null) {
            redisConnection.close();
        }
    }

    private static final class RedisLock implements SimpleLock {

        private final String key;
        private final RedisConnectionFactory redisConnectionFactory;
        private final LockConfiguration lockConfiguration;

        private RedisLock(String key, RedisConnectionFactory redisConnectionFactory, LockConfiguration lockConfiguration) {
            this.key = key;
            this.redisConnectionFactory = redisConnectionFactory;
            this.lockConfiguration = lockConfiguration;
        }

        @Override
        public void unlock() {
            Expiration keepLockFor = getExpiration(lockConfiguration.getLockAtLeastUntil());
            RedisConnection redisConnection = null;
            // lock at least until is in the past
            if (keepLockFor.getExpirationTimeInMilliseconds() <= 0) {
                try {
                    redisConnection = redisConnectionFactory.getConnection();
                    redisConnection.del(key.getBytes());
                } catch (Exception e) {
                    throw new LockException("Can not remove node", e);
                } finally {
                    close(redisConnection);
                }
            } else {
                try {
                    redisConnection = redisConnectionFactory.getConnection();
                    redisConnection.set(key.getBytes(), buildValue(lockConfiguration.getLockAtMostUntil()), keepLockFor, SetOption.SET_IF_PRESENT);
                } finally {
                    close(redisConnection);
                }
            }
        }
    }

    String buildKey(String lockName) {
        return String.format("%s:%s:%s", keyPrefix, environment, lockName);
    }

    private static byte[] buildValue(Instant lockAtMostUntil) {
        return String.format("ADDED:%s@%s EXP:%s", ClockProvider.now().toString(), getHostname(), lockAtMostUntil.toString()).getBytes();
    }

    static Expiration getExpirationFromValue(byte[] value) {
        Instant expires = Instant.parse(new String(value).split("EXP:")[1]);
        return getExpiration(expires);
    }
}
