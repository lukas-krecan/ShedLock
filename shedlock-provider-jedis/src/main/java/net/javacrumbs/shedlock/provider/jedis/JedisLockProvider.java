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
package net.javacrumbs.shedlock.provider.jedis;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Optional;

/**
 * Uses Redis's `SET resource-name anystring NX PX max-lock-ms-time` as locking mechanism.
 *
 * See https://redis.io/commands/set
 *
 */
public class JedisLockProvider implements LockProvider {

    private static final String keyPrefix = "job-lock:";

    private static final String DEFAULT_ENV = "default";

    private JedisPool jedisPool;
    private String environment;

    public JedisLockProvider(JedisPool jedisPool) {
        this(jedisPool, DEFAULT_ENV);
    }

    public JedisLockProvider(JedisPool jedisPool, String environment) {
        this.jedisPool = jedisPool;
        this.environment = environment;
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        long difference = getDifference(lockConfiguration);
        if (difference > 0) {
            String key = buildKey(lockConfiguration.getName(), this.environment);
            try (Jedis jedis = jedisPool.getResource()) {
                String rez = jedis.set(key, buildValue(), "NX", "PX", difference);
                if (rez != null && "OK".equals(rez)) {
                    return Optional.of(new RedisLock(key, jedisPool));
                }
            }
        }
        return Optional.empty();
    }

    long getDifference(LockConfiguration lockConfiguration) {
        long now = Instant.now().toEpochMilli();
        long mostDiff = lockConfiguration.getLockAtMostUntil().toEpochMilli() - now;
        long leastDiff = lockConfiguration.getLockAtLeastUntil().toEpochMilli() - now;

        long difference = -1;
        if (mostDiff > 0 && leastDiff > 0) {
            difference = Math.max(mostDiff, leastDiff);
        } else if (mostDiff > 0 && leastDiff <= 0) {
            difference = mostDiff;
        } else if (mostDiff <= 0 && leastDiff > 0) {
            difference = leastDiff;
        }
        return difference;
    }

    private static final class RedisLock implements SimpleLock {
        private final String key;
        private final JedisPool jedisPool;

        private RedisLock(String key, JedisPool jedisPool) {
            this.key= key;
            this.jedisPool = jedisPool;
        }

        @Override
        public void unlock() {
            try (Jedis jedis = jedisPool.getResource()){
                jedis.del(key);
            } catch (Exception e) {
                throw new LockException("Can not remove node", e);
            }
        }
    }

    protected static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown host";
        }
    }

    public static String buildKey(String lockName, String env) {
        return keyPrefix + env + ":" + lockName;
    }

    static String buildValue() {
        return "ADDED:" + Instant.now().toString() + "@" + getHostname();
    }
}
