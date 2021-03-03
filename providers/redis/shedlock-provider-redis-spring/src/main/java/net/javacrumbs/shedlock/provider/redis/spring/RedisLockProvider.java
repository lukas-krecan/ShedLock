/**
 * Copyright 2009 the original author or authors.
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
package net.javacrumbs.shedlock.provider.redis.spring;

import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.lang.Boolean.TRUE;
import static net.javacrumbs.shedlock.support.Utils.getHostname;
import static net.javacrumbs.shedlock.support.Utils.toIsoString;
import static org.springframework.data.redis.connection.RedisStringCommands.SetOption.SET_IF_ABSENT;

/**
 * Uses Redis's `SET resource-name anystring NX PX max-lock-ms-time` as locking mechanism.
 * See https://redis.io/commands/set
 */
public class RedisLockProvider implements LockProvider {
    private static final String KEY_PREFIX_DEFAULT = "job-lock";
    private static final String ENV_DEFAULT = "default";

    private final StringRedisTemplate redisTemplate;
    private final String environment;
    private final String keyPrefix;

    public RedisLockProvider(@NonNull RedisConnectionFactory redisConn) {
        this(redisConn, ENV_DEFAULT);
    }

    /**
     * Creates RedisLockProvider
     *
     * @param redisConn   RedisConnectionFactory
     * @param environment environment is part of the key and thus makes sure there is not key conflict between
     *                    multiple ShedLock instances running on the same Redis
     */
    public RedisLockProvider(@NonNull RedisConnectionFactory redisConn, @NonNull String environment) {
        this(redisConn, environment, KEY_PREFIX_DEFAULT);
    }

    /**
     * Creates RedisLockProvider
     *
     * @param redisConn   RedisConnectionFactory
     * @param environment environment is part of the key and thus makes sure there is not key conflict between
     *                    multiple ShedLock instances running on the same Redis
     * @param keyPrefix   prefix of the key in Redis.
     */
    public RedisLockProvider(@NonNull RedisConnectionFactory redisConn, @NonNull String environment, @NonNull String keyPrefix) {
        this(new StringRedisTemplate(redisConn), environment, keyPrefix);
    }

    /**
     * Create RedisLockProvider
     *
     * @param redisTemplate StringRedisTemplate
     * @param environment   environment is part of the key and thus makes sure there is not key conflict between
     *                      multiple ShedLock instances running on the same Redis
     * @param keyPrefix     prefix of the key in Redis.
     */
    public RedisLockProvider(@NonNull StringRedisTemplate redisTemplate, @NonNull String environment, @NonNull String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.environment = environment;
        this.keyPrefix = keyPrefix;
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        String key = buildKey(lockConfiguration.getName());
        Expiration expiration = getExpiration(lockConfiguration.getLockAtMostUntil());
        if (TRUE.equals(tryToSetExpiration(redisTemplate, key, expiration, SET_IF_ABSENT))) {
            return Optional.of(new RedisLock(key, redisTemplate, lockConfiguration));
        } else {
            return Optional.empty();
        }
    }

    private static Expiration getExpiration(Instant until) {
        return Expiration.from(getMsUntil(until), TimeUnit.MILLISECONDS);
    }

    private static long getMsUntil(Instant until) {
        return Duration.between(ClockProvider.now(), until).toMillis();
    }

    private static final class RedisLock extends AbstractSimpleLock {

        private final String key;
        private final StringRedisTemplate redisTemplate;

        private RedisLock(String key, StringRedisTemplate redisTemplate, LockConfiguration lockConfiguration) {
            super(lockConfiguration);
            this.key = key;
            this.redisTemplate = redisTemplate;
        }

        @Override
        public void doUnlock() {
            Expiration keepLockFor = getExpiration(lockConfiguration.getLockAtLeastUntil());
            // lock at least until is in the past
            if (keepLockFor.getExpirationTimeInMilliseconds() <= 0) {
                try {
                    redisTemplate.delete(key);
                } catch (Exception e) {
                    throw new LockException("Can not remove node", e);
                }
            } else {
                tryToSetExpiration(this.redisTemplate, key, keepLockFor, SetOption.SET_IF_PRESENT);
            }
        }
    }


    String buildKey(String lockName) {
        return String.format("%s:%s:%s", keyPrefix, environment, lockName);
    }

    private static Boolean tryToSetExpiration(StringRedisTemplate template, String key, Expiration expiration, SetOption option) {
        return template.execute(connection -> {
            byte[] serializedKey = ((RedisSerializer<String>) template.getKeySerializer()).serialize(key);
            byte[] serializedValue = ((RedisSerializer<String>) template.getValueSerializer()).serialize(String.format("ADDED:%s@%s", toIsoString(ClockProvider.now()), getHostname()));
            return connection.set(serializedKey, serializedValue, expiration, option);
        }, false);
    }

    public static class Builder {
        private final StringRedisTemplate redisTemplate;
        private String environment = ENV_DEFAULT;
        private String keyPrefix = KEY_PREFIX_DEFAULT;

        public Builder(@NonNull RedisConnectionFactory redisConnectionFactory) {
            this.redisTemplate = new StringRedisTemplate(redisConnectionFactory);
        }

        public Builder(@NonNull StringRedisTemplate redisTemplate) {
            this.redisTemplate = redisTemplate;
        }

        public Builder environment(@NonNull String environment) {
            this.environment = environment;
            return this;
        }

        public Builder keyPrefix(@NonNull String keyPrefix) {
            this.keyPrefix = keyPrefix;
            return this;
        }

        public RedisLockProvider build() {
            return new RedisLockProvider(redisTemplate, environment, keyPrefix);
        }
    }
}
