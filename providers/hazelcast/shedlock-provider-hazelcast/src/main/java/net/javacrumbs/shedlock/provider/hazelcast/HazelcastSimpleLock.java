package net.javacrumbs.shedlock.provider.hazelcast;

import net.javacrumbs.shedlock.core.SimpleLock;

/**
 * Implementation of {@link SimpleLock} for unlock {@link HazelcastLock}.
 */
class HazelcastSimpleLock implements SimpleLock {

    private final String lockName;

    private final HazelcastLockProvider lockProvider;

    HazelcastSimpleLock(final HazelcastLockProvider lockProvider, final String lockName) {
        this.lockProvider = lockProvider;
        this.lockName = lockName;
    }


    @Override
    public void unlock() {
        lockProvider.unlock(lockName);
    }
}
