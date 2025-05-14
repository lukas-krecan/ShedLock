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
package net.javacrumbs.shedlock.provider.redis.spring;

import static java.lang.Boolean.TRUE;
import static net.javacrumbs.shedlock.support.Utils.getHostname;
import static net.javacrumbs.shedlock.support.Utils.toIsoString;
import static org.springframework.data.redis.connection.RedisStringCommands.SetOption.SET_IF_ABSENT;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Uses Redis's `SET resource-name anystring NX PX max-lock-ms-time` as locking
 * mechanism. See https://redis.io/commands/set
 */
public class RedisLockProvider implements ExtensibleLockProvider {
    private static final String KEY_PREFIX_DEFAULT = "job-lock";
    private static final String ENV_DEFAULT = "default";

    /*
     * https://redis.io/docs/latest/develop/use/patterns/distributed-locks/
     * */
    private static final RedisScript<Long> delLuaScript = new DefaultRedisScript(
            """
        if redis.call("get",KEYS[1]) == ARGV[1] then
            return redis.call("del",KEYS[1])
        else
            return 0
        end
        """,
            Long.class);

    private static final RedisScript<Long> updLuaScript = new DefaultRedisScript(
            """
        if redis.call('get', KEYS[1]) == ARGV[1] then
           return redis.call('pexpire', KEYS[1], ARGV[2])
        else
           return 0
        end
        """,
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String environment;
    private final String keyPrefix;

    public RedisLockProvider(@NonNull RedisConnectionFactory redisConn) {
        this(redisConn, ENV_DEFAULT);
    }

    /**
     * Creates RedisLockProvider
     *
     * @param redisConn
     *            RedisConnectionFactory
     * @param environment
     *            environment is part of the key and thus makes sure there is not
     *            key conflict between multiple ShedLock instances running on the
     *            same Redis
     */
    public RedisLockProvider(@NonNull RedisConnectionFactory redisConn, @NonNull String environment) {
        this(redisConn, environment, KEY_PREFIX_DEFAULT);
    }

    /**
     * Creates RedisLockProvider
     *
     * @param redisConn
     *            RedisConnectionFactory
     * @param environment
     *            environment is part of the key and thus makes sure there is not
     *            key conflict between multiple ShedLock instances running on the
     *            same Redis
     * @param keyPrefix
     *            prefix of the key in Redis.
     */
    public RedisLockProvider(
            @NonNull RedisConnectionFactory redisConn, @NonNull String environment, @NonNull String keyPrefix) {
        this(new StringRedisTemplate(redisConn), environment, keyPrefix);
    }

    /**
     * Create RedisLockProvider
     *
     * @param redisTemplate
     *            StringRedisTemplate
     * @param environment
     *            environment is part of the key and thus makes sure there is not
     *            key conflict between multiple ShedLock instances running on the
     *            same Redis
     * @param keyPrefix
     *            prefix of the key in Redis.
     */
    public RedisLockProvider(
            @NonNull StringRedisTemplate redisTemplate, @NonNull String environment, @NonNull String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.environment = environment;
        this.keyPrefix = keyPrefix;
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        String key = buildKey(lockConfiguration.getName());
        String uniqueValue = buildValue();
        Expiration expiration = getExpiration(lockConfiguration.getLockAtMostUntil());
        if (TRUE.equals(createKey(redisTemplate, key, uniqueValue, expiration))) {
            return Optional.of(new RedisLock(key, uniqueValue, redisTemplate, lockConfiguration));
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
        private final String value;
        private final StringRedisTemplate redisTemplate;

        private RedisLock(
                String key, String value, StringRedisTemplate redisTemplate, LockConfiguration lockConfiguration) {
            super(lockConfiguration);
            this.key = key;
            this.value = value;
            this.redisTemplate = redisTemplate;
        }

        @Override
        public void doUnlock() {
            Expiration keepLockFor = getExpiration(lockConfiguration.getLockAtLeastUntil());
            // lock at least until is in the past
            if (keepLockFor.getExpirationTimeInMilliseconds() <= 0) {
                try {
                    redisTemplate.execute(delLuaScript, List.of(key), value);
                } catch (Exception e) {
                    throw new LockException("Can not remove node", e);
                }
            } else {
                updateExpiration(this, keepLockFor);
            }
        }

        @Override
        public Optional<SimpleLock> doExtend(LockConfiguration newConfiguration) {
            Expiration expiration = getExpiration(newConfiguration.getLockAtMostUntil());
            if (TRUE.equals(updateExpiration(this, expiration))) {
                return Optional.of(new RedisLock(key, value, redisTemplate, newConfiguration));
            }
            return Optional.empty();
        }
    }

    String buildKey(String lockName) {
        return String.format("%s:%s:%s", keyPrefix, environment, lockName);
    }

    String buildValue() {
        return String.format("ADDED:%s@%s:%s", toIsoString(ClockProvider.now()), getHostname(), UUID.randomUUID());
    }

    private static Boolean createKey(StringRedisTemplate template, String key, String value, Expiration expiration) {
        return template.execute(
                connection -> {
                    byte[] serializedKey = ((RedisSerializer<String>) template.getKeySerializer()).serialize(key);
                    byte[] serializedValue = ((RedisSerializer<String>) template.getValueSerializer()).serialize(value);
                    return connection.set(serializedKey, serializedValue, expiration, SET_IF_ABSENT);
                },
                false);
    }

    private static Boolean updateExpiration(RedisLock lock, Expiration expiration) {
        return lock.redisTemplate
                .execute(
                        updLuaScript,
                        List.of(lock.key),
                        lock.value,
                        String.valueOf(expiration.getExpirationTimeInMilliseconds()))
                .equals(1L);
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
