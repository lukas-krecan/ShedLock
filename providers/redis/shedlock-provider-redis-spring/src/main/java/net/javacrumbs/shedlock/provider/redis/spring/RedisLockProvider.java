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
import static net.javacrumbs.shedlock.provider.redis.support.InternalRedisLockProvider.DEFAULT_KEY_PREFIX;
import static net.javacrumbs.shedlock.provider.redis.support.InternalRedisLockProvider.ENV_DEFAULT;
import static org.springframework.data.redis.connection.RedisStringCommands.SetOption.SET_IF_ABSENT;
import static org.springframework.data.redis.connection.RedisStringCommands.SetOption.SET_IF_PRESENT;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.redis.support.InternalRedisLockProvider;
import net.javacrumbs.shedlock.provider.redis.support.InternalRedisLockTemplate;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Uses Redis's `SET resource-name anystring NX PX max-lock-ms-time` as locking
 * mechanism. See https://redis.io/commands/set
 */
public class RedisLockProvider implements ExtensibleLockProvider {
    private final InternalRedisLockProvider internalRedisLockProvider;

    public RedisLockProvider(RedisConnectionFactory redisConn) {
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
    public RedisLockProvider(RedisConnectionFactory redisConn, String environment) {
        this(redisConn, environment, DEFAULT_KEY_PREFIX);
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
    public RedisLockProvider(RedisConnectionFactory redisConn, String environment, String keyPrefix) {
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
    public RedisLockProvider(StringRedisTemplate redisTemplate, String environment, String keyPrefix) {
        this(redisTemplate, environment, keyPrefix, false);
    }

    RedisLockProvider(StringRedisTemplate redisTemplate, String environment, String keyPrefix, boolean safeUpdate) {
        this.internalRedisLockProvider = new InternalRedisLockProvider(
                new SpringRedisLockTemplate(redisTemplate), environment, keyPrefix, safeUpdate);
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        return internalRedisLockProvider.lock(lockConfiguration);
    }

    public static class Builder {
        private final StringRedisTemplate redisTemplate;
        private String environment = ENV_DEFAULT;
        private String keyPrefix = DEFAULT_KEY_PREFIX;
        private boolean safeUpdate = false;

        public Builder(RedisConnectionFactory redisConnectionFactory) {
            this.redisTemplate = new StringRedisTemplate(redisConnectionFactory);
        }

        public Builder(StringRedisTemplate redisTemplate) {
            this.redisTemplate = redisTemplate;
        }

        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public Builder keyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
            return this;
        }

        /**
         * When enabled, the lock will not be released or extended when the lock is held by somebody else.
         *
         * @param safeUpdate When set to true and the lock is held for more than lockAtMostFor, and the lock
         *                  is already held by somebody else, we don't release/extend the lock.
         */
        public Builder safeUpdate(boolean safeUpdate) {
            this.safeUpdate = safeUpdate;
            return this;
        }

        public RedisLockProvider build() {
            return new RedisLockProvider(redisTemplate, environment, keyPrefix, safeUpdate);
        }
    }

    private record SpringRedisLockTemplate(StringRedisTemplate template) implements InternalRedisLockTemplate {

        @Override
        public boolean setIfAbsent(String key, String value, long expirationMs) {
            return set(key, value, expirationMs, SET_IF_ABSENT);
        }

        @Override
        public boolean setIfPresent(String key, String value, long expirationMs) {
            return set(key, value, expirationMs, SET_IF_PRESENT);
        }

        private boolean set(String key, String value, long expirationMs, RedisStringCommands.SetOption setOption) {
            return TRUE.equals(template.execute(
                    connection -> {
                        byte[] serializedKey = ((RedisSerializer<String>) template.getKeySerializer()).serialize(key);
                        byte[] serializedValue =
                                ((RedisSerializer<String>) template.getValueSerializer()).serialize(value);
                        return connection
                                .stringCommands()
                                .set(
                                        serializedKey,
                                        serializedValue,
                                        Expiration.from(expirationMs, TimeUnit.MILLISECONDS),
                                        setOption);
                    },
                    false));
        }

        @Override
        @Nullable
        public Object eval(String script, String key, String... values) {
            return template.execute(new DefaultRedisScript<>(script, Integer.class), List.of(key), (Object[]) values);
        }

        @Override
        public void delete(String key) {
            template.delete(key);
        }
    }
}
