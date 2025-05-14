package net.javacrumbs.shedlock.provider.redis.testsupport;

import static java.time.Duration.ZERO;
import static java.time.Duration.ofSeconds;

import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.test.support.AbstractExtensibleLockProviderIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * The fix for this use-case only exists in Redis LockProvider implementations.
 * When we will fix this in all LockProviders, we can move this test to the base class, removing the need for this class.
 */
public abstract class AbstractExtensibleLockProviderRedisIntegrationTest
        extends AbstractExtensibleLockProviderIntegrationTest {

    @Test
    public void unlockingAfterExpirationShouldBeNoOp() {
        int lockDurationSeconds = 2;
        SimpleLock lock1 = lock(ofSeconds(lockDurationSeconds));
        sleepFor(ofSeconds(lockDurationSeconds + 1));
        SimpleLock lock2 = lock(ofSeconds(lockDurationSeconds));
        lock1.unlock();
        assertLocked(LOCK_NAME1);
    }

    @Test
    public void extendAfterExpirationShouldBeNoOp() {
        int lockDurationSeconds = 2;
        SimpleLock lock1 = lock(ofSeconds(lockDurationSeconds));
        sleepFor(ofSeconds(lockDurationSeconds + 1));
        SimpleLock lock2 = lock(ofSeconds(lockDurationSeconds));
        lock1.extend(ofSeconds(lockDurationSeconds * 5), ZERO);
        sleepFor(ofSeconds(lockDurationSeconds + 1));
        assertUnlocked(LOCK_NAME1);
    }
}
