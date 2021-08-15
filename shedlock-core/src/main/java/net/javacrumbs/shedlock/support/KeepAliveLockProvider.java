package net.javacrumbs.shedlock.support;

import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * LockProvider that keeps the lock `alive`. In the middle of lockAtMostFor period tries to extend the lock for
 * lockAtMostFor period. For example, if the lockAtMostFor is 10 minutes the lock is extended every 5 minutes for 10 minutes
 * until the lock is released. If the process dies, the lock is automatically released after lockAtMostFor period as usual.
 *
 * Wraps ExtensibleLockProvider that implements the actual locking.
 */
public class KeepAliveLockProvider implements ExtensibleLockProvider {
    private final ExtensibleLockProvider wrapped;
    private final ScheduledExecutorService executorService;

    private static final Logger logger = LoggerFactory.getLogger(KeepAliveLockProvider.class);

    public KeepAliveLockProvider(ExtensibleLockProvider wrapped, ScheduledExecutorService executorService) {
        this.wrapped = wrapped;
        this.executorService = executorService;
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        Optional<SimpleLock> lock = wrapped.lock(lockConfiguration);
        if (lock.isPresent()) {
            LockExtender lockExtender = new LockExtender(lockConfiguration, lock.get());
            long extensionPeriodMs = lockExtender.getLockExtensionPeriod().toMillis();
            ScheduledFuture<?> future = executorService.scheduleAtFixedRate(
                lockExtender,
                extensionPeriodMs,
                extensionPeriodMs,
                MILLISECONDS
            );
            lockExtender.setFuture(future);
            return Optional.of(new SimpleLockWrapper(lockExtender, future));
        } else {
            return lock;
        }
    }

    private static class LockExtender implements Runnable {
        private final LockConfiguration lockConfiguration;
        private final Duration lockExtensionPeriod;
        private SimpleLock lock;
        private Duration remainingLockAtLeastFor;
        private ScheduledFuture<?> future;

        private LockExtender(LockConfiguration lockConfiguration, SimpleLock lock) {
            this.lockConfiguration = lockConfiguration;
            this.lock = lock;
            this.lockExtensionPeriod = lockConfiguration.getLockAtMostFor().dividedBy(2);
            this.remainingLockAtLeastFor = lockConfiguration.getLockAtLeastFor();
        }

        @Override
        public void run() {
            remainingLockAtLeastFor = remainingLockAtLeastFor.minus(lockExtensionPeriod);
            if (remainingLockAtLeastFor.isNegative()) {
                remainingLockAtLeastFor = Duration.ZERO;
            }
            Optional<SimpleLock> extendedLock = lock.extend(lockConfiguration.getLockAtMostFor(), remainingLockAtLeastFor);
            if (extendedLock.isPresent()) {
                logger.debug("Lock {} extended for {}", lockConfiguration.getName(), lockConfiguration.getLockAtMostFor());
                lock = extendedLock.get();
            } else {
                logger.warn("Can't extend lock {}", lockConfiguration.getName());
                if (future != null) {
                    future.cancel(false);
                }
            }
        }

        private Duration getLockExtensionPeriod() {
            return lockExtensionPeriod;
        }

        private SimpleLock getLock() {
            return lock;
        }

        private void setFuture(ScheduledFuture<?> future) {
            this.future = future;
        }
    }

    private static class SimpleLockWrapper implements SimpleLock {
        private final LockExtender lockExtender;
        private final ScheduledFuture<?> future;

        private SimpleLockWrapper(LockExtender lockExtender, ScheduledFuture<?> future) {
            this.lockExtender = lockExtender;
            this.future = future;
        }

        @Override
        public void unlock() {
            future.cancel(false);
            getLock().unlock();
        }

        @Override
        @NonNull
        public Optional<SimpleLock> extend(@NonNull Instant lockAtMostUntil, @NonNull Instant lockAtLeastUntil) {
            return getLock().extend(lockAtMostUntil, lockAtLeastUntil);
        }

        @Override
        @NonNull
        public Optional<SimpleLock> extend(@NonNull Duration lockAtMostFor, @NonNull Duration lockAtLeastFor) {
            return getLock().extend(lockAtMostFor, lockAtLeastFor);
        }

        private SimpleLock getLock() {
            return lockExtender.getLock();
        }
    }
}
