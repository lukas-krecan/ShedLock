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
package net.javacrumbs.shedlock.provider.hazelcast4;

import static net.javacrumbs.shedlock.core.ClockProvider.now;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HazelcastLockProvider.
 *
 * <p>
 * Implementation of {@link LockProvider} using Hazelcast for storing and
 * sharing lock information and mechanisms between a cluster's members
 *
 * <p>
 * Below, the mechanisms : - The Lock, an instance of {@link HazelcastLock}, is
 * obtained / created when : -- the lock is not already locked by other process
 * (lock - referenced by its name - is not present in the Hazelcast locks store
 * OR unlockable) -- the lock is expired : {@link Instant#now()} &gt;
 * {@link HazelcastLock#timeToLive} where unlockTime have by default the same
 * value of {@link HazelcastLock#lockAtMostUntil} and can have the value of
 * {@link HazelcastLock#lockAtLeastUntil} if unlock action is used --- expired
 * object is removed -- the lock is owned by not available member of Hazelcast
 * cluster member --- no owner object is removed - Unlock action : -- removes
 * lock object when {@link HazelcastLock#lockAtLeastUntil} is not come --
 * override value of {@link HazelcastLock#timeToLive} with
 * {@link HazelcastLock#lockAtLeastUntil} (its default value is the same of
 * {@link HazelcastLock#lockAtLeastUntil}
 */
public class HazelcastLockProvider implements LockProvider {

    private static final Logger log = LoggerFactory.getLogger(HazelcastLockProvider.class);

    static final String LOCK_STORE_KEY_DEFAULT = "shedlock_storage";
    private static final Duration DEFAULT_LOCK_LEASE_TIME = Duration.ofSeconds(30);

    /**
     * Key used for get the lock container (an {@link IMap}) inside
     * {@link #hazelcastInstance}. By default : {@link #LOCK_STORE_KEY_DEFAULT}
     */
    private final String lockStoreKey;

    /** Instance of the Hazelcast engine used by the application. */
    private final HazelcastInstance hazelcastInstance;

    private final long lockLeaseTimeMs;

    /**
     * Instantiate the provider.
     *
     * @param hazelcastInstance
     *            The Hazelcast engine used by the application.
     */
    public HazelcastLockProvider(HazelcastInstance hazelcastInstance) {
        this(hazelcastInstance, LOCK_STORE_KEY_DEFAULT);
    }

    /**
     * Instantiate the provider.
     *
     * @param hazelcastInstance
     *            The Hazelcast engine used by the application
     * @param lockStoreKey
     *            The key where the locks are stored (by default
     *            {@link #LOCK_STORE_KEY_DEFAULT}).
     */
    public HazelcastLockProvider(HazelcastInstance hazelcastInstance, String lockStoreKey) {
        this(hazelcastInstance, lockStoreKey, DEFAULT_LOCK_LEASE_TIME);
    }

    /**
     * Instantiate the provider.
     *
     * @param hazelcastInstance
     *            The com.hazelcast.core.Hazelcast engine used by the application
     * @param lockStoreKey
     *            The key where the locks are stored (by default
     *            {@link #LOCK_STORE_KEY_DEFAULT}).
     * @param lockLeaseTime
     *            When lock is being obtained there is a Hazelcast lock used to make
     *            it thread-safe. This lock should be released quite fast but if the
     *            process dies while holding the lock, it is held forever.
     *            lockLeaseTime is used as a safety-net for such situations.
     */
    public HazelcastLockProvider(HazelcastInstance hazelcastInstance, String lockStoreKey, Duration lockLeaseTime) {
        this.hazelcastInstance = hazelcastInstance;
        this.lockStoreKey = lockStoreKey;
        this.lockLeaseTimeMs = lockLeaseTime.toMillis();
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        log.trace("lock - Attempt : {}", lockConfiguration);
        String lockName = lockConfiguration.getName();
        IMap<String, HazelcastLock> store = getStore();
        try {
            // lock the map key entry
            store.lock(lockName, keyLockTime(lockConfiguration), TimeUnit.MILLISECONDS);
            // just one thread at a time, in the cluster, can run this code
            // each thread waits until the lock to be unlock
            if (tryLock(lockConfiguration)) {
                return Optional.of(new HazelcastSimpleLock(this, lockConfiguration));
            }
        } finally {
            // released the map lock for the others threads
            store.unlock(lockName);
        }
        return Optional.empty();
    }

    private long keyLockTime(LockConfiguration lockConfiguration) {
        Duration between = Duration.between(now(), lockConfiguration.getLockAtMostUntil());
        return between.toMillis();
    }

    private boolean tryLock(LockConfiguration lockConfiguration) {
        String lockName = lockConfiguration.getName();
        HazelcastLock lock = getLock(lockName);
        if (isUnlocked(lock)) {
            log.debug("lock - lock obtained, it wasn't locked : conf={}", lockConfiguration);
            addNewLock(lockConfiguration);
            return true;
        } else if (lock.isExpired(now())) {
            log.debug(
                    "lock - lock obtained, it was locked but expired : oldLock={};  conf={}", lock, lockConfiguration);
            replaceLock(lockName, lockConfiguration);
            return true;
        } else {
            log.debug("lock - already locked : currentLock={};  conf={}", lock, lockConfiguration);
            return false;
        }
    }

    private IMap<String, HazelcastLock> getStore() {
        return hazelcastInstance.getMap(lockStoreKey);
    }

    @Nullable
    HazelcastLock getLock(String lockName) {
        return getStore().get(lockName);
    }

    private void removeLock(String lockName) {
        getStore().delete(lockName);
        log.debug("lock store - lock deleted : {}", lockName);
    }

    private void addNewLock(LockConfiguration lockConfiguration) {
        HazelcastLock lock = HazelcastLock.fromConfigurationWhereTtlIsUntilTime(lockConfiguration);
        log.trace("lock store - new lock created from configuration : {}", lockConfiguration);
        String lockName = lockConfiguration.getName();
        getStore().put(lockName, lock);
        log.debug("lock store - new lock added : {}", lock);
    }

    private void replaceLock(String lockName, LockConfiguration lockConfiguration) {
        log.debug("lock store - replace lock : {}", lockName);
        removeLock(lockName);
        addNewLock(lockConfiguration);
    }

    private boolean isUnlocked(@Nullable HazelcastLock lock) {
        return lock == null;
    }

    /**
     * Unlock the lock with its name. Don't use unless you know what you are doing, unlocking a lock held by an active
     * task may result in multiple concurrent task executions.
     */
    public void riskyUnlock(String lockName) {
        log.info("riskyUnlock : {}", lockName);
        unlock(lockName);
    }

    void unlock(String lockName) {
        log.trace("unlock - attempt : {}", lockName);
        IMap<String, HazelcastLock> store = getStore();
        try {
            store.lock(lockName, lockLeaseTimeMs, TimeUnit.MILLISECONDS);
            unlockProperly(getLock(lockName));
        } finally {
            store.unlock(lockName);
        }
    }

    private void unlockProperly(@Nullable HazelcastLock lock) {
        if (isUnlocked(lock)) {
            log.debug("unlock - it is already unlocked");
            return;
        }
        String lockName = lock.getName();
        Instant lockAtLeastInstant = lock.getLockAtLeastUntil();
        if (!now().isBefore(lockAtLeastInstant)) {
            removeLock(lockName);
            log.debug("unlock - done : {}", lock);
        } else {
            log.debug("unlock - it doesn't unlock, least time is not passed : {}", lock);
            HazelcastLock newLock = HazelcastLock.fromLockWhereTtlIsReduceToLeastTime(lock);
            getStore().put(lockName, newLock);
        }
    }
}
