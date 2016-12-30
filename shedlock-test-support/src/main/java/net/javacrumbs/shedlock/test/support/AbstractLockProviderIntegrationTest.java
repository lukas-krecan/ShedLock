package net.javacrumbs.shedlock.test.support;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.MILLIS;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractLockProviderIntegrationTest {
    private static final String LOCK_NAME1 = "name";

    protected abstract LockProvider getLockProvider();

    protected abstract void assertUnlocked(String lockName);

    protected abstract void assertLocked(String lockName);


    @Test
    public void shouldCreateLock() {
        Optional<SimpleLock> lock = getLockProvider().lock(lockConfig(LOCK_NAME1));
        assertThat(lock).isNotEmpty();

        assertLocked(LOCK_NAME1);
        lock.get().unlock();
        assertUnlocked(LOCK_NAME1);

    }

    @Test
    public void shouldNotReturnSecondLock() {
        Optional<SimpleLock> lock = getLockProvider().lock(lockConfig(LOCK_NAME1));
        assertThat(lock).isNotEmpty();
        assertThat(getLockProvider().lock(lockConfig(LOCK_NAME1))).isEmpty();
        lock.get().unlock();
    }

    @Test
    public void shouldCreateTwoIndependentLocks() {
        Optional<SimpleLock> lock1 = getLockProvider().lock(lockConfig(LOCK_NAME1));
        assertThat(lock1).isNotEmpty();

        Optional<SimpleLock> lock2 = getLockProvider().lock(lockConfig("name2"));
        assertThat(lock2).isNotEmpty();

        lock1.get().unlock();
        lock2.get().unlock();
    }

    @Test
    public void shouldLockTwiceInARow() {
        Optional<SimpleLock> lock1 = getLockProvider().lock(lockConfig(LOCK_NAME1));
        assertThat(lock1).isNotEmpty();
        lock1.get().unlock();

        Optional<SimpleLock> lock2 = getLockProvider().lock(lockConfig(LOCK_NAME1));
        assertThat(lock2).isNotEmpty();
        lock2.get().unlock();
    }

    @Test
    public void shouldTimeout() throws InterruptedException {
        LockConfiguration configWithShortTimeout = lockConfig(LOCK_NAME1, 2);
        Optional<SimpleLock> lock1 = getLockProvider().lock(configWithShortTimeout);
        assertThat(lock1).isNotEmpty();

        Thread.sleep(2);

        Optional<SimpleLock> lock2 = getLockProvider().lock(configWithShortTimeout);
        assertThat(lock2).isNotEmpty();
    }

    @Test
    public void shouldBeAbleToLockRightAfterUnlock() {
        LockConfiguration lockConfiguration = lockConfig(LOCK_NAME1);
        LockProvider lockProvider = getLockProvider();
        for (int i = 0; i < 100; i++) {
            Optional<SimpleLock> lock = lockProvider.lock(lockConfiguration);
            assertThat(lockProvider.lock(lockConfiguration)).isEmpty();
            assertThat(lock).isNotEmpty();
            lock.get().unlock();
        }
    }

    @Test
    public void fuzzTestShouldPass() throws ExecutionException, InterruptedException {
        new FuzzTester(getLockProvider()).doFuzzTest();
    }

    private static LockConfiguration lockConfig(String name) {
        return lockConfig(name, TimeUnit.MINUTES.toMillis(5));
    }

    private static LockConfiguration lockConfig(String name, long timeoutMillis) {
        return new LockConfiguration(name, Instant.now().plus(timeoutMillis, MILLIS));
    }

}