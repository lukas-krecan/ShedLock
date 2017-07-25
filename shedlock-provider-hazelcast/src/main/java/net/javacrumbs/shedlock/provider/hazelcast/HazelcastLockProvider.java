package net.javacrumbs.shedlock.provider.hazelcast;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

/**
 *
 */
public class HazelcastLockProvider implements LockProvider {

    private static final Logger log = LoggerFactory.getLogger(HazelcastLockProvider.class);

    protected static final String LOCK_STORE_KEY_DEFAULT = "shedlock_storage";

    /**
     * Key used for get the lock container (an {@link IMap}) inside {@link #hazelcastInstance}.
     */
    protected final String lockStoreKey;

    /**
     * Instance of the Hazelcast engine used by the application.
     */
    protected HazelcastInstance hazelcastInstance;

    /**
     * Instanciate the provider.
     *
     * @param hazelcastInstance The Hazelcast engine used by the application.
     */
    public HazelcastLockProvider(final HazelcastInstance hazelcastInstance) {
        this(hazelcastInstance, LOCK_STORE_KEY_DEFAULT);
    }

    /**
     * Instanciate the provider.
     *
     * @param hazelcastInstance The Hzelcast engine used by the application
     * @param lockStoreKey      The where the locks store is located inside {@link #hazelcastInstance}.
     */
    public HazelcastLockProvider(final HazelcastInstance hazelcastInstance, final String lockStoreKey) {
        this.hazelcastInstance = hazelcastInstance;
        this.lockStoreKey = lockStoreKey;
    }


    @Override
    public Optional<SimpleLock> lock(final LockConfiguration lockConfiguration) {
        log.trace("lock - Attempt : {}", lockConfiguration);
        final Instant now = Instant.now();
        final String lockName = lockConfiguration.getName();
        final IMap<String, HazelcastLock> store = getStore();
        try {
            // lock the map key entry
            store.lock(lockName);
            // just one thread at a time, in the cluster, can run this code
            // each thread waits until the lock to be released
            if (tryLock(lockConfiguration, now)) {
                return Optional.of(() -> unlock(lockName));
            }
        } finally {
            // released the map lock for the others threads
            store.unlock(lockName);
        }
        return Optional.empty();
    }

    private boolean tryLock(final LockConfiguration lockConfiguration, final Instant now) {
        final String lockName = lockConfiguration.getName();
        final HazelcastLock lock = getLock(lockName);
        if (isUnlocked(lock)) {
            log.debug("lock - lock obtained, it wasn't locked : {}", lockConfiguration);
            addNewLock(lockConfiguration);
            return true;
        } else if (isExpired(lock, now)) {
            log.debug("lock - lock obtained, it was locked but expired : {}", lockConfiguration);
            removeLock(lockName);
            addNewLock(lockConfiguration);
            return true;
        } else if (isLockedByDownClusterMember(lock)) {
            log.debug("lock - lock obtained, it was locked by a down cluster membre : {}", lockConfiguration);
            removeLock(lockName);
            addNewLock(lockConfiguration);
            return true;
        } else {
            log.debug("lock - already locked : {}", lockConfiguration);
            return false;
        }
    }

    private IMap<String, HazelcastLock> getStore() {
        return hazelcastInstance.getMap(lockStoreKey);
    }

    protected HazelcastLock getLock(final String lockName) {
        final IMap<String, HazelcastLock> store = getStore();
        return store.get(lockName);
    }

    private void removeLock(final String lockName) {
        final IMap<String, HazelcastLock> store = getStore();
        store.delete(lockName);
        log.debug("lock store - lock deleted : {}", lockName);
    }

    private void addNewLock(final LockConfiguration lockConfiguration) {
        final String localMemberUuid = getLocalMemberUuid();
        final HazelcastLock lock = HazelcastLock.fromLockConfiguration(lockConfiguration, localMemberUuid);
        final String lockName = lockConfiguration.getName();
        final IMap<String, HazelcastLock> store = getStore();
        store.put(lockName, lock);
        log.debug("lock store - new lock added : {}", lock);
    }

    private String getLocalMemberUuid() {
        return hazelcastInstance.getCluster().getLocalMember().getUuid();
    }

    protected boolean isUnlocked(final HazelcastLock lock) {
        return lock == null;
    }

    protected boolean isExpired(final HazelcastLock lock, final Instant now) {
        final Instant unlockTime = lock.getUnlockTime();
        return now.isAfter(unlockTime);
    }

    protected boolean isLockedByDownClusterMember(final HazelcastLock lock) {
        final String memberUuid = lock.getClusterMemberUuid();
        final boolean membreIsUp = hazelcastInstance.getCluster().getMembers().stream().anyMatch(member -> member.getUuid().equals(memberUuid));
        return !membreIsUp;
    }

    /**
     * Unlock a lock with its name.
     *
     * @param lockName
     */
    protected void unlock(final String lockName) {
        log.trace("unlock - attempt : {}", lockName);
        final Instant now = Instant.now();
        final IMap<String, HazelcastLock> store = getStore();
        try {
            store.lock(lockName);
            final HazelcastLock lock = getLock(lockName);
            unlockProperly(lock, now);
        } finally {
            store.unlock(lockName);
        }
    }

    private void unlockProperly(final HazelcastLock lock, final Instant now) {
        if (isUnlocked(lock)) {
            log.debug("unlock - it is already unlocked");
            return;
        }
        final String lockName = lock.getName();
        final Instant lockAtLeastInstant = lock.getLockAtLeastUntil();
        if (now.isAfter(lockAtLeastInstant)) {
            removeLock(lockName);
            log.debug("unlock - done : {}", lock);
        } else {
            log.debug("unlock - it doesn't unlock, least time is not passed : {}", lock);
            lock.setUnlockTime(lockAtLeastInstant);
            final IMap<String, HazelcastLock> store = getStore();
            store.put(lockName, lock);
        }

    }
}
