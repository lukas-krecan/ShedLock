package net.javacrumbs.shedlock.core;

import net.javacrumbs.shedlock.support.annotation.NonNull;

import java.time.Duration;
import java.util.Optional;

public final class LockExtender {
    private static final ThreadLocal<SimpleLock> activeLock = ThreadLocal.withInitial(() -> null);

    private LockExtender() { }

    /**
     * Extends active lock. Is based on a thread local variable so it might not work in case of async processing.
     *
     * @throws LockCanNotBeExtendedException when the lock can not be extended due to expired lock
     * @throws NoActiveLockException         when there is no active lock in the thread local
     * @throws UnsupportedOperationException when the LockProvider does not support lock extension.
     */
    public static void extendActiveLock(@NonNull Duration lockAtMostFor, @NonNull Duration lockAtLeastFor) {
        SimpleLock lock = activeLock.get();
        if (lock == null) throw new NoActiveLockException();
        Optional<SimpleLock> newLock = lock.extend(lockAtMostFor, lockAtLeastFor);
        if (newLock.isPresent()) {
            activeLock.set(newLock.get());
        } else {
            throw new LockCanNotBeExtendedException();
        }
    }

    static void startLock(@NonNull SimpleLock lock) {
        activeLock.set(lock);
    }

    static SimpleLock endLock() {
        SimpleLock lock = activeLock.get();
        activeLock.remove();
        return lock;
    }

    public static class LockExtensionException extends RuntimeException {
        public LockExtensionException(String message) {
            super(message);
        }
    }

    public static class NoActiveLockException extends LockExtensionException {
        public NoActiveLockException() {
            super("No active lock in current thread, please make sure that you execute LockExtender.extendActiveLock in locked context.");
        }
    }

    public static class LockCanNotBeExtendedException extends LockExtensionException {
        public LockCanNotBeExtendedException() {
            super("Lock can not be extended, most likely it already expired.");
        }
    }
}
