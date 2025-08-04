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
package net.javacrumbs.shedlock.provider.redis.jedis6;

import static net.javacrumbs.shedlock.provider.redis.support.InternalRedisLockProvider.DEFAULT_KEY_PREFIX;
import static net.javacrumbs.shedlock.provider.redis.support.InternalRedisLockProvider.ENV_DEFAULT;
import static redis.clients.jedis.params.SetParams.setParams;

import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.redis.support.InternalRedisLockProvider;
import net.javacrumbs.shedlock.provider.redis.support.InternalRedisLockTemplate;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.commands.JedisCommands;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.util.Pool;

/**
 * Uses Redis's `SET resource-name anystring NX PX max-lock-ms-time` as locking
 * mechanism.
 *
 * <p>
 * See <a href="https://redis.io/commands/set">Set command</a>
 */
public class JedisLockProvider implements ExtensibleLockProvider {

    private final InternalRedisLockProvider internalRedisLockProvider;

    public JedisLockProvider(Pool<Jedis> jedisPool) {
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
    public JedisLockProvider(Pool<Jedis> jedisPool, String environment) {
        this(jedisPool, environment, false);
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
     * @param safeUpdate When set to true and the lock is held for more than lockAtMostFor, and the lock
     *                   is already held by somebody else, we don't release/extend the lock.
     */
    public JedisLockProvider(Pool<Jedis> jedisPool, String environment, boolean safeUpdate) {
        this.internalRedisLockProvider = new InternalRedisLockProvider(
                new JedisPoolTemplate(jedisPool), environment, DEFAULT_KEY_PREFIX, safeUpdate);
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
    public JedisLockProvider(JedisCommands jedisCommands, String environment) {
        this(jedisCommands, environment, false);
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
     * @param safeUpdate When set to true and the lock is held for more than lockAtMostFor, and the lock
     *                   is already held by somebody else, we don't release/extend the lock.
     */
    public JedisLockProvider(JedisCommands jedisCommands, String environment, boolean safeUpdate) {
        this.internalRedisLockProvider = new InternalRedisLockProvider(
                new JedisCommandsTemplate(jedisCommands), environment, DEFAULT_KEY_PREFIX, safeUpdate);
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        return internalRedisLockProvider.lock(lockConfiguration);
    }

    private record JedisPoolTemplate(Pool<Jedis> jedisPool) implements InternalRedisLockTemplate {
        @Override
        public boolean setIfAbsent(String key, String value, long expirationMs) {
            return set(key, value, setParams().nx().px(expirationMs));
        }

        @Override
        public boolean setIfPresent(String key, String value, long expirationMs) {
            return set(key, value, setParams().xx().px(expirationMs));
        }

        private boolean set(String key, String value, SetParams params) {
            try (Jedis jedis = jedisPool.getResource()) {
                return "OK".equals(jedis.set(key, value, params));
            }
        }

        @Override
        public Object eval(String script, String key, String... values) {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.eval(script, List.of(key), List.of(values));
            }
        }

        @Override
        public void delete(String key) {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.del(key);
            }
        }
    }

    private record JedisCommandsTemplate(JedisCommands jedisCommands) implements InternalRedisLockTemplate {
        @Override
        public boolean setIfAbsent(String key, String value, long expirationMs) {
            return set(key, value, setParams().nx().px(expirationMs));
        }

        @Override
        public boolean setIfPresent(String key, String value, long expirationMs) {
            return set(key, value, setParams().xx().px(expirationMs));
        }

        private boolean set(String key, String value, SetParams params) {
            return "OK".equals(jedisCommands.set(key, value, params));
        }

        @Override
        public Object eval(String script, String key, String... values) {
            return jedisCommands.eval(script, List.of(key), List.of(values));
        }

        @Override
        public void delete(String key) {
            jedisCommands.del(key);
        }
    }
}
