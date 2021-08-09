package net.javacrumbs.shedlock.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class LockKeeper {

    private static final Logger logger = LoggerFactory.getLogger(LockKeeper.class);

    private volatile SimpleLock lock;
    private final ScheduledExecutorService scheduler;

    LockKeeper(SimpleLock lock, LockConfiguration lockConfig) {
        this.lock = lock;
        this.scheduler = scheduleExtension(lockConfig);
    }

    void endLock() {
        scheduler.shutdown();
        lock.unlock();
    }

    private ScheduledExecutorService scheduleExtension(LockConfiguration lockConfig) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        long delay = lockConfig.getLockAtMostFor().get(ChronoUnit.SECONDS) / 2;
        scheduler.scheduleWithFixedDelay(
                () -> extendLock(lockConfig),
                delay,
                delay == 0 ? 1 : delay,
                TimeUnit.SECONDS
        );

        return scheduler;
    }

    private void extendLock(LockConfiguration lockConfig) {
        logger.debug("Extending lock: {} for: {}", lockConfig.getName(), lockConfig.getLockAtMostFor());
        try {
            lock.extend(lockConfig.getLockAtMostFor(), lockConfig.getLockAtLeastFor())
                    .ifPresent(simpleLock -> lock = simpleLock);
        } catch (Exception e) {
            logger.error("Failed to extend log: {}", lockConfig.getName(), e);
        }
    }
}
