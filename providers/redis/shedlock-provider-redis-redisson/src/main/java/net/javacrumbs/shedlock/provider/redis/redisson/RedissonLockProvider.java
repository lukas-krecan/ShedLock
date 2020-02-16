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
package net.javacrumbs.shedlock.provider.redis.redisson;

import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static net.javacrumbs.shedlock.support.Utils.getHostname;
import static net.javacrumbs.shedlock.support.Utils.toIsoString;

public class RedissonLockProvider implements LockProvider {

    private static final String KEY_PREFIX = "job-lock";
    private static final String ENV_DEFAULT = "default";

    private final RedissonClient redissonClient;
    private final String environment;

    public RedissonLockProvider(@NotNull RedissonClient redissonClient) {
        this(redissonClient, ENV_DEFAULT);
    }

    public RedissonLockProvider(@NotNull RedissonClient redissonClient, @NotNull String environment) {
        this.redissonClient = redissonClient;
        this.environment = environment;
    }

    @Override
    @NotNull
    public Optional<SimpleLock> lock(@NotNull LockConfiguration lockConfiguration) {
        long expireTime = getMsUntil(lockConfiguration.getLockAtMostUntil());

        String key = buildKey(lockConfiguration.getName());

        RLock lock = redissonClient.getLock(key);

        try {
            if (lock.tryLock(0, expireTime, TimeUnit.MILLISECONDS)) {
                return Optional.of(new RedissonLock(lockConfiguration, lock, redissonClient));
            }
        } catch (InterruptedException e) {
            // ignore
        }
        return Optional.empty();
    }

    private static final class RedissonLock extends AbstractSimpleLock {
        private final RLock lock;
        private final RedissonClient redissonClient;

        private RedissonLock(LockConfiguration lockConfiguration, RLock lock, RedissonClient redissonClient) {
            super(lockConfiguration);
            this.lock = lock;
            this.redissonClient = redissonClient;
        }

        @Override
        public void doUnlock() {
            lock.unlock();
        }
    }

    private static long getMsUntil(Instant instant) {
        return Duration.between(Instant.now(), instant).toMillis();
    }

    String buildKey(String lockName) {
        return String.format("%s:%s:%s", KEY_PREFIX, environment, lockName);
    }

    private static String buildValue() {
        return String.format("ADDED:%s@%s", toIsoString(Instant.now()), getHostname());
    }
}
