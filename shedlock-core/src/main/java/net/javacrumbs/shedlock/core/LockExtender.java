package net.javacrumbs.shedlock.core;

import java.time.Duration;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Optional;
import net.javacrumbs.shedlock.support.annotation.Nullable;

public final class LockExtender {
    // Using deque here instead of a simple thread local to be able to handle nested
    // locks.
    private static final ThreadLocal<Deque<SimpleLock>> activeLocks = ThreadLocal.withInitial(LinkedList::new);

    private LockExtender() {}

    /**
     * Extends active lock. Is based on a thread local variable, so it might not
     * work in case of async processing. In case of nested locks, extends the
     * innermost lock.
     *
     * @throws LockCanNotBeExtendedException
     *             when the lock can not be extended due to expired lock
     * @throws NoActiveLockException
     *             when there is no active lock in the thread local
     * @throws UnsupportedOperationException
     *             when the LockProvider does not support lock extension.
     */
    public static void extendActiveLock(Duration lockAtMostFor, Duration lockAtLeastFor) {
        SimpleLock lock = locks().peekLast();
        if (lock == null) throw new NoActiveLockException();
        Optional<SimpleLock> newLock = lock.extend(lockAtMostFor, lockAtLeastFor);
        if (newLock.isPresent()) {
            // removing and adding here should be safe as it's a thread local variable and
            // the changes are
            // only visible in the current thread.
            locks().removeLast();
            locks().addLast(newLock.get());
        } else {
            throw new LockCanNotBeExtendedException();
        }
    }

    private static Deque<SimpleLock> locks() {
        return activeLocks.get();
    }

    static void startLock(SimpleLock lock) {
        locks().addLast(lock);
    }

    @Nullable
    static SimpleLock endLock() {
        SimpleLock lock = locks().pollLast();
        // we want to clean up the thread local variable when there are no locks
        if (locks().isEmpty()) {
            activeLocks.remove();
        }
        return lock;
    }

    public static class LockExtensionException extends RuntimeException {
        public LockExtensionException(String message) {
            super(message);
        }
    }

    public static class NoActiveLockException extends LockExtensionException {
        public NoActiveLockException() {
            super(
                    "No active lock in current thread, please make sure that you execute LockExtender.extendActiveLock in locked context.");
        }
    }

    public static class LockCanNotBeExtendedException extends LockExtensionException {
        public LockCanNotBeExtendedException() {
            super("Lock can not be extended, most likely it already expired.");
        }
    }
}
