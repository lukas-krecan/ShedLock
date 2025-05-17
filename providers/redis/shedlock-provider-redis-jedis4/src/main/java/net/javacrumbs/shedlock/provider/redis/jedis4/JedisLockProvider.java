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
package net.javacrumbs.shedlock.provider.redis.jedis4;

import static net.javacrumbs.shedlock.support.Utils.getHostname;
import static net.javacrumbs.shedlock.support.Utils.toIsoString;
import static redis.clients.jedis.params.SetParams.setParams;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.commands.JedisCommands;
import redis.clients.jedis.util.Pool;

/**
 * Uses Redis's `SET resource-name anystring NX PX max-lock-ms-time` as locking
 * mechanism.
 *
 * <p>
 * See <a href="https://redis.io/commands/set">Set command</a>
 */
public class JedisLockProvider implements ExtensibleLockProvider {

    private static final String KEY_PREFIX = "job-lock";
    private static final String ENV_DEFAULT = "default";

    private final JedisTemplate jedisTemplate;
    private final String environment;

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

    public JedisLockProvider(@NonNull Pool<Jedis> jedisPool) {
        this(jedisPool, ENV_DEFAULT);
    }

    /**
     * Creates JedisLockProvider
     *
     * @param jedisPool
     *            Jedis connection pool
     * @param environment
     *            environment is part of the key and thus makes sure there is not
     *            key conflict between multiple ShedLock instances running on the
     *            same Redis
     */
    public JedisLockProvider(@NonNull Pool<Jedis> jedisPool, @NonNull String environment) {
        this.jedisTemplate = new JedisPoolTemplate(jedisPool);
        this.environment = environment;
    }

    /**
     * Creates JedisLockProvider
     *
     * @param jedisCommands
     *            implementation of JedisCommands.
     * @param environment
     *            environment is part of the key and thus makes sure there is not
     *            key conflict between multiple ShedLock instances running on the
     *            same Redis
     */
    public JedisLockProvider(@NonNull JedisCommands jedisCommands, @NonNull String environment) {
        this.jedisTemplate = new JedisCommandsTemplate(jedisCommands);
        this.environment = environment;
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        long expireTime = getMsUntil(lockConfiguration.getLockAtMostUntil());

        String key = buildKey(lockConfiguration.getName(), this.environment);
        String uniqueLockValue = buildValue();

        if (jedisTemplate.create(key, uniqueLockValue, expireTime)) {
            return Optional.of(new RedisLock(key, uniqueLockValue, this, lockConfiguration));
        }

        return Optional.empty();
    }

    private Optional<SimpleLock> extend(RedisLock currentLock, LockConfiguration lockConfiguration) {
        long expireTime = getMsUntil(lockConfiguration.getLockAtMostUntil());

        if (extendKeyExpiration(currentLock, expireTime)) {
            return Optional.of(new RedisLock(currentLock.key, currentLock.value, this, lockConfiguration));
        }

        return Optional.empty();
    }

    private boolean extendKeyExpiration(RedisLock currentLock, long expiration) {
        return jedisTemplate.upd(currentLock, expiration);
    }

    private void deleteKey(String key, String value) {
        jedisTemplate.del(key, value);
    }

    private static final class RedisLock extends AbstractSimpleLock {
        private final String key;
        private final String value;
        private final JedisLockProvider jedisLockProvider;

        private RedisLock(
                String key, String value, JedisLockProvider jedisLockProvider, LockConfiguration lockConfiguration) {
            super(lockConfiguration);
            this.key = key;
            this.value = value;
            this.jedisLockProvider = jedisLockProvider;
        }

        @Override
        public void doUnlock() {
            long keepLockFor = getMsUntil(lockConfiguration.getLockAtLeastUntil());

            // lock at least until is in the past
            if (keepLockFor <= 0) {
                try {
                    jedisLockProvider.deleteKey(key, this.value);
                } catch (Exception e) {
                    throw new LockException("Can not remove node", e);
                }
            } else {
                jedisLockProvider.extendKeyExpiration(this, keepLockFor);
            }
        }

        @Override
        @NonNull
        protected Optional<SimpleLock> doExtend(@NonNull LockConfiguration newConfiguration) {
            return jedisLockProvider.extend(this, newConfiguration);
        }
    }

    private static long getMsUntil(Instant instant) {
        return Duration.between(ClockProvider.now(), instant).toMillis();
    }

    static String buildKey(String lockName, String env) {
        return String.format("%s:%s:%s", KEY_PREFIX, env, lockName);
    }

    private static String buildValue() {
        return String.format("ADDED:%s@%s:%s", toIsoString(ClockProvider.now()), getHostname(), UUID.randomUUID());
    }

    private interface JedisTemplate {
        boolean create(String key, String value, long expirationMs);

        boolean upd(RedisLock lock, long expirationMs);

        void del(String key, String value);
    }

    private record JedisPoolTemplate(Pool<Jedis> jedisPool) implements JedisTemplate {

        @Override
        public boolean create(String key, String value, long expirationMs) {
            try (Jedis jedis = jedisPool.getResource()) {
                return "OK".equals(jedis.set(key, value, setParams().nx().px(expirationMs)));
            }
        }

        @Override
        public boolean upd(RedisLock lock, long expirationMs) {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.eval(updLuaScript, List.of(lock.key), List.of(lock.value, String.valueOf(expirationMs)))
                        .equals(1L);
            }
        }

        @Override
        public void del(String key, String value) {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.eval(delLuaScript, 1, key, value);
            }
        }
    }

    private record JedisCommandsTemplate(JedisCommands jedisCommands) implements JedisTemplate {

        @Override
        public boolean create(String key, String value, long expirationMs) {
            return "OK".equals(jedisCommands.set(key, value, setParams().nx().px(expirationMs)));
        }

        @Override
        public boolean upd(RedisLock lock, long expirationMs) {
            return jedisCommands
                    .eval(updLuaScript, List.of(lock.key), List.of(lock.value, String.valueOf(expirationMs)))
                    .equals(1L);
        }

        @Override
        public void del(String key, String value) {
            jedisCommands.eval(delLuaScript, 1, key, value);
        }
    }
}
