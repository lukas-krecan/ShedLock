/**
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.test.support;

import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static java.lang.Thread.sleep;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractLockProviderIntegrationTest {
    protected final String LOCK_NAME1 = UUID.randomUUID().toString();
    public static final Duration LOCK_AT_LEAST_FOR = Duration.of(2, SECONDS);

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
        LockProvider lockProvider = getLockProvider();
        Optional<SimpleLock> lock = lockProvider.lock(lockConfig(LOCK_NAME1));
        assertThat(lock).isNotEmpty();
        assertThat(lockProvider.lock(lockConfig(LOCK_NAME1))).isEmpty();
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
        LockProvider lockProvider = getLockProvider();
        Optional<SimpleLock> lock1 = lockProvider.lock(lockConfig(LOCK_NAME1));
        assertThat(lock1).isNotEmpty();
        lock1.get().unlock();

        Optional<SimpleLock> lock2 = lockProvider.lock(lockConfig(LOCK_NAME1));
        assertThat(lock2).isNotEmpty();
        lock2.get().unlock();
    }

    @Test
    public void shouldTimeout() throws InterruptedException {
        doTestTimeout(Duration.ofMillis(50));
    }

    protected void doTestTimeout(Duration lockAtMostFor) throws InterruptedException {
        LockConfiguration configWithShortTimeout = lockConfig(LOCK_NAME1, lockAtMostFor, Duration.ZERO);
        Optional<SimpleLock> lock1 = getLockProvider().lock(configWithShortTimeout);
        assertThat(lock1).isNotEmpty();

        sleep(lockAtMostFor.toMillis() * 2);
        assertUnlocked(LOCK_NAME1);

        Optional<SimpleLock> lock2 = getLockProvider().lock(lockConfig(LOCK_NAME1, Duration.ofMillis(50), Duration.ZERO));
        assertThat(lock2).isNotEmpty();
        lock2.get().unlock();
    }


    @Test
    public void shouldBeAbleToLockRightAfterUnlock() {
        LockConfiguration lockConfiguration = lockConfig(LOCK_NAME1);
        for (int i = 0; i < 10; i++) {
            Optional<SimpleLock> lock = getLockProvider().lock(lockConfiguration);
            assertThat(lock).describedAs("Successfully locked").isNotEmpty();
            assertThat(getLockProvider().lock(lockConfiguration)).isEmpty();
            assertThat(lock).isNotEmpty();
            lock.get().unlock();
        }
    }

    @Test
    public void fuzzTestShouldPass() throws ExecutionException, InterruptedException {
        new FuzzTester(getLockProvider()).doFuzzTest();
    }

    @Test
    public void shouldLockAtLeastFor() throws InterruptedException {
        doTestShouldLockAtLeastFor(100);
    }

    protected void doTestShouldLockAtLeastFor(int sleepForMs) throws InterruptedException {
        // Lock for LOCK_AT_LEAST_FOR - we do not expect the lock to be released before this time
        Optional<SimpleLock> lock1 = getLockProvider().lock(lockConfig(LOCK_NAME1, LOCK_AT_LEAST_FOR.multipliedBy(2), LOCK_AT_LEAST_FOR));
        assertThat(lock1).describedAs("Should be locked").isNotEmpty();
        lock1.get().unlock();

        // Even though we have unlocked the lock, it will be held for some time
        assertThat(getLockProvider().lock(lockConfig(LOCK_NAME1))).describedAs(getClass().getName() + "Can not acquire lock, grace period did not pass yet").isEmpty();

        // Let's wait for the lock to be automatically released
        sleep(LOCK_AT_LEAST_FOR.toMillis() + sleepForMs);

        // Should be able to acquire now
        Optional<SimpleLock> lock3 = getLockProvider().lock(lockConfig(LOCK_NAME1));
        assertThat(lock3).describedAs(getClass().getName() + "Can acquire the lock after grace period").isNotEmpty();
        lock3.get().unlock();
    }

    protected void sleepFor(Duration duration) {
        try {
            sleep(duration.toMillis());
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    protected static LockConfiguration lockConfig(String name) {
        return lockConfig(name, Duration.of(5, MINUTES), Duration.ZERO);
    }

    protected static LockConfiguration lockConfig(String name, Duration lockAtMostFor, Duration lockAtLeastFor) {
        return new LockConfiguration(ClockProvider.now(), name, lockAtMostFor, lockAtLeastFor);
   }
}
