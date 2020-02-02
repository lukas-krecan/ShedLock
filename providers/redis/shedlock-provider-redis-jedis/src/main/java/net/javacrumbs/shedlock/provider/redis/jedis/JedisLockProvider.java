/**
 * Copyright 2009-2019 the original author or authors.
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

import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
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

    private final JedisTemplate jedisTemplate;
    private final String environment;

    public JedisLockProvider(@NotNull Pool<Jedis> jedisPool) {
        this(jedisPool, ENV_DEFAULT);
    }

    /**
     * Creates JedisLockProvider
     *
     * @param jedisPool   Jedis connection pool
     * @param environment environment is part of the key and thus makes sure there is not key conflict between
     *                    multiple ShedLock instances running on the same Redis
     */
    public JedisLockProvider(@NotNull Pool<Jedis> jedisPool, @NotNull String environment) {
        this.jedisTemplate = new JedisPoolTemplate(jedisPool);
        this.environment = environment;
    }

    /**
     * Creates JedisLockProvider
     *
     * @param jedisCluster Jedis cluster
     * @param environment  environment is part of the key and thus makes sure there is not key conflict between
     *                     multiple ShedLock instances running on the same Redis
     */
    public JedisLockProvider(@NotNull JedisCluster jedisCluster, @NotNull String environment) {
        this.jedisTemplate = new JedisClusterTemplate(jedisCluster);
        this.environment = environment;
    }

    @Override
    @NotNull
    public Optional<SimpleLock> lock(@NotNull LockConfiguration lockConfiguration) {
        long expireTime = getMsUntil(lockConfiguration.getLockAtMostUntil());

        String key = buildKey(lockConfiguration.getName(), this.environment);

        String rez = jedisTemplate.set(key,
            buildValue(),
            SET_IF_NOT_EXIST,
            SET_EXPIRE_TIME_IN_MS,
            expireTime);

        if ("OK".equals(rez)) {
            return Optional.of(new RedisLock(key, jedisTemplate, lockConfiguration));
        }

        return Optional.empty();
    }

    private static final class RedisLock extends AbstractSimpleLock {
        private final String key;
        private final JedisTemplate jedisTemplate;

        private RedisLock(String key, JedisTemplate jedisTemplate, LockConfiguration lockConfiguration) {
            super(lockConfiguration);
            this.key = key;
            this.jedisTemplate = jedisTemplate;
        }

        @Override
        public void doUnlock() {
            long keepLockFor = getMsUntil(lockConfiguration.getLockAtLeastUntil());

            // lock at least until is in the past
            if (keepLockFor <= 0) {
                try {
                    jedisTemplate.del(key);
                } catch (Exception e) {
                    throw new LockException("Can not remove node", e);
                }
            } else {
                jedisTemplate.set(key,
                    buildValue(),
                    SET_IF_EXIST,
                    SET_EXPIRE_TIME_IN_MS,
                    keepLockFor);
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

    private interface JedisTemplate {
        String set(String key, String value, String nxxx, String expx, long time);

        void del(String key);
    }

    private static class JedisPoolTemplate implements JedisTemplate {
        private final Pool<Jedis> jedisPool;

        private JedisPoolTemplate(Pool<Jedis> jedisPool) {
            this.jedisPool = jedisPool;
        }

        @Override
        public String set(String key, String value, String nxxx, String expx, long time) {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.set(key, value, nxxx, expx, time);
            }
        }

        @Override
        public void del(String key) {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.del(key);
            }
        }
    }

    private static class JedisClusterTemplate implements JedisTemplate {
        private final JedisCluster jedisCluster;

        private JedisClusterTemplate(JedisCluster jedisCluster) {
            this.jedisCluster = jedisCluster;
        }

        @Override
        public String set(String key, String value, String nxxx, String expx, long time) {
            return jedisCluster.set(key, value, nxxx, expx, time);
        }

        @Override
        public void del(String key) {
            jedisCluster.del(key);
        }
    }
}
