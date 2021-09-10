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
package net.javacrumbs.shedlock.provider.ignite;

import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;

import java.time.Instant;
import java.util.Optional;

import static net.javacrumbs.shedlock.support.Utils.getHostname;

/**
 * Distributed lock using Apache Ignite.
 * <p>
 * It uses a {@link String} key (lock name) and {@link LockValue} value.
 * </p>
 *
 * <p>lockedAt and lockedBy are just for troubleshooting and are not read by the code.</p>
 *
 * Creating a lock:
 * <ol>
 * <li>
 * If there is no locks with given name, try to use {@link IgniteCache#putIfAbsent}.
 * </li>
 * <li>
 * If there is a lock with given name, and its lockUntil is before or equal {@code now},
 * try to use {@link IgniteCache#replace}.
 * </li>
 * <li>
 * Otherwise, return {@link Optional#empty}.
 * </li>
 * </ol>
 *
 * Extending a lock:
 * <ol>
 * <li>
 * If there is a lock with given name and hostname, and its lockUntil is after {@code now}, try to use
 * {@link IgniteCache#replace} to set lockUntil to {@link LockConfiguration#getLockAtMostUntil}.
 * </li>
 * <li>
 * Otherwise, return {@link Optional#empty}.
 * </li>
 * </ol>
 *
 * Unlock:
 * <ol>
 * <li>
 * If there is a lock with given name and hostname, try to use {@link IgniteCache#replace} to set lockUntil to
 * {@link LockConfiguration#getUnlockTime}.
 * </li>
 * </ol>
 */
public class IgniteLockProvider implements ExtensibleLockProvider {
    /** Default ShedLock cache name. */
    public static final String DEFAULT_SHEDLOCK_CACHE_NAME = "shedLock";

    /** ShedLock cache. */
    private final IgniteCache<String, LockValue> cache;

    /**
     * @param ignite Ignite instance.
     */
    public IgniteLockProvider(@NonNull Ignite ignite) {
        this(ignite, DEFAULT_SHEDLOCK_CACHE_NAME);
    }

    /**
     * @param ignite Ignite instance.
     * @param shedLockCacheName ShedLock cache name to use instead of default.
     */
    public IgniteLockProvider(@NonNull Ignite ignite, @NonNull String shedLockCacheName) {
        this.cache = ignite.getOrCreateCache(shedLockCacheName);
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockCfg) {
        Instant now = Instant.now();
        String key = lockCfg.getName();

        LockValue newVal = new LockValue(now, lockCfg.getLockAtMostUntil(), getHostname());
        LockValue oldVal = cache.get(key);

        if (oldVal == null) {
            if (cache.putIfAbsent(key, newVal))
                return Optional.of(new IgniteLock(lockCfg, this));

            return Optional.empty();
        }

        if (!now.isBefore(oldVal.getLockUntil()) && cache.replace(key, oldVal, newVal))
            return Optional.of(new IgniteLock(lockCfg, this));

        return Optional.empty();
    }

    /**
     * If there is a lock with given name and hostname, try to use {@link IgniteCache#replace} to set lockUntil to
     * {@link LockConfiguration#getLockAtMostUntil}.
     *
     * @param lockCfg Lock configuration.
     * @return New lock if succeed extended. Empty, otherwise.
     */
    private Optional<SimpleLock> extend(LockConfiguration lockCfg) {
        Instant now = Instant.now();

        String key = lockCfg.getName();
        LockValue oldVal = cache.get(key);

        if (oldVal == null || !oldVal.getLockedBy().equals(getHostname()) || !oldVal.getLockUntil().isAfter(now))
            return Optional.empty();

        LockValue newVal = oldVal.withLockUntil(lockCfg.getLockAtMostUntil());

        if (cache.replace(key, oldVal, newVal))
            return Optional.of(new IgniteLock(lockCfg, this));

        return Optional.empty();
    }

    /**
     * If there is a lock with given name and hostname, try to use {@link IgniteCache#replace} to set lockUntil to
     * {@code now}.
     *
     * @param lockCfg Lock configuration.
     */
    private void unlock(LockConfiguration lockCfg) {
        String key = lockCfg.getName();
        LockValue oldVal = cache.get(key);

        if (oldVal != null && oldVal.getLockedBy().equals(getHostname())) {
            LockValue newVal = oldVal.withLockUntil(lockCfg.getUnlockTime());

            cache.replace(key, oldVal, newVal);
        }
    }

    /**
     * Ignite lock.
     */
    private static final class IgniteLock extends AbstractSimpleLock {
        /** Ignite lock provider. */
        private final IgniteLockProvider lockProvider;

        /**
         * @param lockCfg Lock configuration.
         * @param lockProvider Ignite lock provider.
         */
        private IgniteLock(LockConfiguration lockCfg, IgniteLockProvider lockProvider) {
            super(lockCfg);

            this.lockProvider = lockProvider;
        }

        /** {@inheritDoc} */
        @Override
        public void doUnlock() {
            lockProvider.unlock(lockConfiguration);
        }

        /** {@inheritDoc} */
        @Override
        public Optional<SimpleLock> doExtend(LockConfiguration newLockConfiguration) {
            return lockProvider.extend(newLockConfiguration);
        }
    }
}
