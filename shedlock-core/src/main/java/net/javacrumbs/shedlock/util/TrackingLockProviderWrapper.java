package net.javacrumbs.shedlock.util;

import static java.util.Collections.newSetFromMap;
import static java.util.Collections.synchronizedSet;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.annotation.NonNull;

/**
 * Wraps a LockProvider and keeps track of active locks. You can use the getActiveLocks() method
 * to obtain the list of active locks. Unlocking the returned locks will most likely lead to unpredictable
 * results - the lock will be released another node can get the lock while the original task is still running.
 */
public class TrackingLockProviderWrapper implements LockProvider {
    private final LockProvider wrapped;
    private final Set<SimpleLock> activeLocks = synchronizedSet(newSetFromMap(new IdentityHashMap<>()));

    public TrackingLockProviderWrapper(LockProvider wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        Optional<SimpleLock> result = wrapped.lock(lockConfiguration);
        if (result.isPresent()) {
            SimpleLock wrappedLock = new SimpleLockWrapper(result.get(), lockConfiguration);
            activeLocks.add(wrappedLock);
            return Optional.of(wrappedLock);
        } else {
            return Optional.empty();
        }
    }

    public Collection<SimpleLock> getActiveLocks() {
        return Collections.unmodifiableSet(activeLocks);
    }

    private class SimpleLockWrapper implements SimpleLockWithConfiguration {
        private final SimpleLock wrappedLock;
        private final LockConfiguration lockConfiguration;
        private final AtomicBoolean locked = new AtomicBoolean(true);

        private SimpleLockWrapper(SimpleLock wrappedLock, LockConfiguration lockConfiguration) {
            this.wrappedLock = wrappedLock;
            this.lockConfiguration = lockConfiguration;
        }

        @Override
        public void unlock() {
            try {
                // Unlocking only once - unlocking twice is dangerous as it's likely that the second unlock will
                // unlock a lock held by another process
                if (locked.compareAndSet(true, false)) {
                    wrappedLock.unlock();
                }
            } finally {
                activeLocks.remove(this);
            }
        }

        @Override
        @NonNull
        public Optional<SimpleLock> extend(@NonNull Duration lockAtMostFor, @NonNull Duration lockAtLeastFor) {
            return wrappedLock.extend(lockAtMostFor, lockAtLeastFor);
        }

        @Override
        public LockConfiguration getLockConfiguration() {
            return lockConfiguration;
        }
    }
}
