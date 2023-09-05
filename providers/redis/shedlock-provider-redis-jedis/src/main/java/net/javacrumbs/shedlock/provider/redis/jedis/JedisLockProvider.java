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
package net.javacrumbs.shedlock.provider.redis.jedis;

import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.util.Pool;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static net.javacrumbs.shedlock.support.Utils.getHostname;
import static net.javacrumbs.shedlock.support.Utils.toIsoString;
import static redis.clients.jedis.params.SetParams.setParams;

/**
 * Uses Redis's `SET resource-name anystring NX PX max-lock-ms-time` as locking mechanism.
 * <p>
 * See <a href="https://redis.io/commands/set">Set command</a>
 */
public class JedisLockProvider implements ExtensibleLockProvider {

    private static final String KEY_PREFIX = "job-lock";
    private static final String ENV_DEFAULT = "default";

    private final JedisTemplate jedisTemplate;
    private final String environment;

    public JedisLockProvider(@NonNull Pool<Jedis> jedisPool) {
        this(jedisPool, ENV_DEFAULT);
    }

    /**
     * Creates JedisLockProvider
     *
     * @param jedisPool   Jedis connection pool
     * @param environment environment is part of the key and thus makes sure there is not key conflict between
     *                    multiple ShedLock instances running on the same Redis
     */
    public JedisLockProvider(@NonNull Pool<Jedis> jedisPool, @NonNull String environment) {
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
    public JedisLockProvider(@NonNull JedisCluster jedisCluster, @NonNull String environment) {
        this.jedisTemplate = new JedisClusterTemplate(jedisCluster);
        this.environment = environment;
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        long expireTime = getMsUntil(lockConfiguration.getLockAtMostUntil());

        String key = buildKey(lockConfiguration.getName(), this.environment);

        String rez = jedisTemplate.set(key,
            buildValue(),
            setParams().nx().px(expireTime)
        );

        if ("OK".equals(rez)) {
            return Optional.of(new RedisLock(key, this, lockConfiguration));
        }

        return Optional.empty();
    }

    private Optional<SimpleLock> extend(LockConfiguration lockConfiguration) {
        long expireTime = getMsUntil(lockConfiguration.getLockAtMostUntil());

        String key = buildKey(lockConfiguration.getName(), this.environment);

        String rez = extendKeyExpiration(key, expireTime);

        if ("OK".equals(rez)) {
            return Optional.of(new RedisLock(key, this, lockConfiguration));
        }

        return Optional.empty();
    }

    private String extendKeyExpiration(String key, long expiration) {
        return jedisTemplate.set(key, buildValue(), setParams().xx().px(expiration));
    }

    private void deleteKey(String key) {
        jedisTemplate.del(key);
    }


    private static final class RedisLock extends AbstractSimpleLock {
        private final String key;
        private final JedisLockProvider jedisLockProvider;

        private RedisLock(String key, JedisLockProvider jedisLockProvider, LockConfiguration lockConfiguration) {
            super(lockConfiguration);
            this.key = key;
            this.jedisLockProvider = jedisLockProvider;
        }

        @Override
        public void doUnlock() {
            long keepLockFor = getMsUntil(lockConfiguration.getLockAtLeastUntil());

            // lock at least until is in the past
            if (keepLockFor <= 0) {
                try {
                    jedisLockProvider.deleteKey(key);
                } catch (Exception e) {
                    throw new LockException("Can not remove node", e);
                }
            } else {
                jedisLockProvider.extendKeyExpiration(key, keepLockFor);
            }
        }

        @Override
        @NonNull
        protected Optional<SimpleLock> doExtend(@NonNull LockConfiguration newConfiguration) {
            return jedisLockProvider.extend(newConfiguration);
        }
    }

    private static long getMsUntil(Instant instant) {
        return Duration.between(ClockProvider.now(), instant).toMillis();
    }

    static String buildKey(String lockName, String env) {
        return String.format("%s:%s:%s", KEY_PREFIX, env, lockName);
    }

    private static String buildValue() {
        return String.format("ADDED:%s@%s", toIsoString(ClockProvider.now()), getHostname());
    }

    private interface JedisTemplate {
        String set(String key, String value, SetParams setParams);

        void del(String key);
    }

    private static class JedisPoolTemplate implements JedisTemplate {
        private final Pool<Jedis> jedisPool;

        private JedisPoolTemplate(Pool<Jedis> jedisPool) {
            this.jedisPool = jedisPool;
        }

        @Override
        public String set(String key, String value, SetParams setParams) {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.set(key, value, setParams);
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
        public String set(String key, String value, SetParams setParams) {
            return jedisCluster.set(key, value, setParams);
        }

        @Override
        public void del(String key) {
            jedisCluster.del(key);
        }
    }
}
