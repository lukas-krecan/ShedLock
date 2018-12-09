/**
 * Copyright 2009-2018 the original author or authors.
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
package net.javacrumbs.shedlock.provider.redis.jedis;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import redis.clients.jedis.Jedis;
import redis.clients.util.Pool;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static net.javacrumbs.shedlock.support.Utils.getHostname;
import static net.javacrumbs.shedlock.support.Utils.toIsoString;

/**
 * Uses Redis's `SET resource-name anystring NX PX max-lock-ms-time` as locking mechanism.
 * <p>
 * See https://redis.io/commands/set
 */
public class JedisLockProvider implements LockProvider {

    private static final String KEY_PREFIX = "job-lock";
    private static final String ENV_DEFAULT = "default";

    // Redis Flags
    private static final String SET_IF_NOT_EXIST = "NX";
    private static final String SET_IF_EXIST = "XX";
    private static final String SET_EXPIRE_TIME_IN_MS = "PX";

    private final Pool<Jedis> jedisPool;
    private final String environment;

    public JedisLockProvider(Pool<Jedis> jedisPool) {
        this(jedisPool, ENV_DEFAULT);
    }

    /**
      * Creates JedisLockProvider
      * @param jedisPool Jedis connection pool
      * @param environment environment is part of the key and thus makes sure there is not key conflict between
      *        multiple ShedLock instances running on the same Redis
      */
    public JedisLockProvider(Pool<Jedis> jedisPool, String environment) {
        this.jedisPool = jedisPool;
        this.environment = environment;
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        long expireTime = getMsUntil(lockConfiguration.getLockAtMostUntil());

        String key = buildKey(lockConfiguration.getName(), this.environment);

        try (Jedis jedis = jedisPool.getResource()) {
            String rez = jedis.set(key,
                buildValue(),
                SET_IF_NOT_EXIST,
                SET_EXPIRE_TIME_IN_MS,
                expireTime);

            if (rez != null && "OK".equals(rez)) {
                return Optional.of(new RedisLock(key, jedisPool, lockConfiguration));
            }
        }
        return Optional.empty();
    }

    private static final class RedisLock implements SimpleLock {
        private final String key;
        private final Pool<Jedis> jedisPool;
        private final LockConfiguration lockConfiguration;

        private RedisLock(String key, Pool<Jedis> jedisPool, LockConfiguration lockConfiguration) {
            this.key = key;
            this.jedisPool = jedisPool;
            this.lockConfiguration = lockConfiguration;
        }

        @Override
        public void unlock() {
            long keepLockFor = getMsUntil(lockConfiguration.getLockAtLeastUntil());

            // lock at least until is in the past
            if (keepLockFor <= 0) {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.del(key);
                } catch (Exception e) {
                    throw new LockException("Can not remove node", e);
                }
            } else {
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.set(key,
                        buildValue(),
                        SET_IF_EXIST,
                        SET_EXPIRE_TIME_IN_MS,
                        keepLockFor);
                }
            }
        }
    }

    private static long getMsUntil(Instant instant) {
        return Duration.between(Instant.now(), instant).toMillis();
    }

    static String buildKey(String lockName, String env) {
        return String.format("%s:%s:%s", KEY_PREFIX, env, lockName);
    }

    private static String buildValue() {
        return String.format("ADDED:%s@%s", toIsoString(Instant.now()), getHostname());
    }
}
