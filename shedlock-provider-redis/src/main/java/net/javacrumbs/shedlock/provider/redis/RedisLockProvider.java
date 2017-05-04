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
package net.javacrumbs.shedlock.provider.redis;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Instant;
import java.util.Optional;

/**
 * Uses Redis as locking mechanism.
 */
public class RedisLockProvider implements LockProvider {

    private static final String keyPrefix = "job-lock:";

    private JedisPool jedisPool;

    static String buildKey(String lockName) {
        return keyPrefix + lockName;
    }

    public RedisLockProvider(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        long difference = getDifference(lockConfiguration);
        if (difference > 0) {
            String key = buildKey(lockConfiguration.getName());
            try (Jedis jedis = jedisPool.getResource()) {
                String rez = jedis.set(key, "value", "NX", "PX", difference);
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
            difference = Math.min(mostDiff, leastDiff);
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
}
