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

import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static java.time.Duration.ZERO;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractExtensibleLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {
    private final Duration originalLockDuration = ofSeconds(2);

    @Override
    protected abstract ExtensibleLockProvider getLockProvider();

    @Test
    public void shouldBeAbleToExtendLock() {
        SimpleLock lock = lock(originalLockDuration);

        SimpleLock newLock = extendLock(lock);

        // wait for the original lock to be released
        sleepFor(originalLockDuration);
        assertLocked(LOCK_NAME1);
        newLock.unlock();
        assertUnlocked(LOCK_NAME1);
    }
    @Test
    public void shouldBeAbleToExtendMultipleTimes() {
        SimpleLock lock = lock(originalLockDuration);


        SimpleLock newLock = extendLock(extendLock(extendLock(lock)));

        // wait for the original lock to be released
        sleepFor(originalLockDuration);
        assertLocked(LOCK_NAME1);
        newLock.unlock();
        assertUnlocked(LOCK_NAME1);
    }


    @Test
    public void shouldNotBeAbleToExtendUnlockedLock() {
        SimpleLock lock = lock(originalLockDuration);
        lock.unlock();
        assertUnlocked(LOCK_NAME1);
        assertInvalidLock(() -> lock.extend(ofSeconds(10), ZERO));
    }

    @Test
    public void shouldNotBeAbleToExtendExpiredLock() {
        Optional<SimpleLock> lock = getLockProvider().lock(lockConfig(LOCK_NAME1, Duration.ofMillis(2), ZERO));
        assertThat(lock).isNotEmpty();
        sleepFor(Duration.ofMillis(4));

        Optional<SimpleLock> newLock = lock.get().extend(ofSeconds(10), ZERO);
        assertThat(newLock).isEmpty();
        assertUnlocked(LOCK_NAME1);
    }


    @Test
    public void shouldBeAbleToExtendAtLeast() {
        SimpleLock lock = lock(ofSeconds(10));

        SimpleLock newLock = extendLock(lock, ofSeconds(9));
        newLock.unlock();
        assertLocked(LOCK_NAME1);
    }

    @Test
    public void lockCanNotBeExtendedTwice() {
        SimpleLock lock = lock(ofSeconds(10));
        extendLock(lock, ofSeconds(9));

        assertInvalidLock(() -> lock.extend(ofSeconds(10), ofSeconds(9)));
    }

    @Test
    public void lockCanNotBeUnlockedAfterExtending() {
        SimpleLock lock = lock(ofSeconds(10));
        extendLock(lock, ofSeconds(9));

        assertInvalidLock(lock::unlock);
    }

    private SimpleLock extendLock(SimpleLock lock) {
        return extendLock(lock, ZERO);
    }
    private SimpleLock extendLock(SimpleLock lock, Duration lockAtLeastFor) {
        Optional<SimpleLock> newLock = lock.extend(ofSeconds(10), lockAtLeastFor);
        assertThat(newLock).isNotEmpty();
        return newLock.get();
    }

    private SimpleLock lock(Duration lockAtMostFor) {
        Optional<SimpleLock> lock = getLockProvider().lock(lockConfig(LOCK_NAME1, lockAtMostFor, ZERO));
        assertThat(lock).isNotEmpty();
        assertLocked(LOCK_NAME1);
        return lock.get();
    }

    private void assertInvalidLock(ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOf(IllegalStateException.class);
    }
}
