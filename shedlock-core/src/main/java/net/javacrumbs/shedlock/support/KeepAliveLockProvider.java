package net.javacrumbs.shedlock.support;

import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
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
import static net.javacrumbs.shedlock.core.ClockProvider.now;

/**
 * LockProvider that keeps the lock `alive`. In the middle of lockAtMostFor period tries to extend the lock for
 * lockAtMostFor period. For example, if the lockAtMostFor is 10 minutes the lock is extended every 5 minutes for 10 minutes
 * until the lock is released. If the process dies, the lock is automatically released after lockAtMostFor period as usual.
 *
 * <b>Does not support lockAtMostFor shorter than 30s.</b> The reason is that with short (subsecond) lockAtMostFor time the
 * time when we attmpt to extend the lock is too close to the expiration time and the lock can expire before we are able to extend it.
 *
 * Wraps ExtensibleLockProvider that implements the actual locking.
 */
public class KeepAliveLockProvider implements LockProvider {
    private final ExtensibleLockProvider wrapped;
    private final ScheduledExecutorService executorService;
    private final Duration minimalLockAtMostFor;

    private static final Logger logger = LoggerFactory.getLogger(KeepAliveLockProvider.class);

    public KeepAliveLockProvider(ExtensibleLockProvider wrapped, ScheduledExecutorService executorService) {
        this(wrapped, executorService, Duration.ofSeconds(30));
    }

    KeepAliveLockProvider(ExtensibleLockProvider wrapped, ScheduledExecutorService executorService, Duration minimalLockAtMostFor) {
        this.wrapped = wrapped;
        this.executorService = executorService;
        this.minimalLockAtMostFor = minimalLockAtMostFor;
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        if (lockConfiguration.getLockAtMostFor().compareTo(minimalLockAtMostFor) < 0) {
            throw new IllegalArgumentException("Can not use KeepAliveLockProvider with lockAtMostFor shorter than " + minimalLockAtMostFor);
        }
        Optional<SimpleLock> lock = wrapped.lock(lockConfiguration);
        return lock.map(simpleLock -> new KeepAliveLock(lockConfiguration, simpleLock, executorService));
    }

    private static class KeepAliveLock extends AbstractSimpleLock {
        private final Duration lockExtensionPeriod;
        private SimpleLock lock;
        private Duration remainingLockAtLeastFor;
        private final ScheduledFuture<?> future;
        private boolean active = true;
        private Instant currentLockAtMostUntil;

        private KeepAliveLock(LockConfiguration lockConfiguration, SimpleLock lock, ScheduledExecutorService executorService) {
            super(lockConfiguration);
            this.lock = lock;
            this.lockExtensionPeriod = lockConfiguration.getLockAtMostFor().dividedBy(2);
            this.remainingLockAtLeastFor = lockConfiguration.getLockAtLeastFor();
            this.currentLockAtMostUntil = lockConfiguration.getLockAtMostUntil();

            long extensionPeriodMs = lockExtensionPeriod.toMillis();
            this.future = executorService.scheduleAtFixedRate(
                this::extendForNextPeriod,
                extensionPeriodMs,
                extensionPeriodMs,
                MILLISECONDS
            );
        }

        private void extendForNextPeriod() {
            // We can have a race-condition when we extend the lock but the `lock` field is accessed before we update it.
            synchronized (this) {
                if (!active) {
                    return;
                }
                if (currentLockAtMostUntil.isBefore(now())) {
                    // Failsafe for cases when we are not able to extend the lock and it expires before the extension
                    // In such case someone else might have already obtained the lock so we can't extend it.
                    stop();
                    return;
                }
                remainingLockAtLeastFor = remainingLockAtLeastFor.minus(lockExtensionPeriod);
                if (remainingLockAtLeastFor.isNegative()) {
                    remainingLockAtLeastFor = Duration.ZERO;
                }
                currentLockAtMostUntil = now().plus(lockConfiguration.getLockAtMostFor());
                Optional<SimpleLock> extendedLock = lock.extend(lockConfiguration.getLockAtMostFor(), remainingLockAtLeastFor);
                if (extendedLock.isPresent()) {
                    lock = extendedLock.get();
                    logger.trace("Lock {} extended for {}", lockConfiguration.getName(), lockConfiguration.getLockAtMostFor());
                } else {
                    logger.warn("Can't extend lock {}", lockConfiguration.getName());
                    stop();
                }
            }
        }

        private void stop() {
            active = false;
            future.cancel(false);
        }

        @Override
        protected void doUnlock() {
            synchronized (this) {
                stop();
                lock.unlock();
            }
        }

        @Override
        protected Optional<SimpleLock> doExtend(LockConfiguration newConfiguration) {
            throw new UnsupportedOperationException("Manual extension of KeepAliveLock is not supported (yet)");
        }
    }
}
