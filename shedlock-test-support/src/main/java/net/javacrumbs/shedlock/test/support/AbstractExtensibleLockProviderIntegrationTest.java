/**
 * Copyright 2009-2019 the original author or authors.
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

import net.javacrumbs.shedlock.core.SimpleLock;
import org.assertj.core.api.ThrowableAssert;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractExtensibleLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    @Test
    public void shouldBeAbleToExtendLock() {
        Duration originalLockDuration = Duration.ofMillis(1_000);
        Optional<SimpleLock> lock = getLockProvider().lock(lockConfig(LOCK_NAME1, originalLockDuration, Duration.ZERO));
        assertThat(lock).isNotEmpty();
        assertLocked(LOCK_NAME1);
        Optional<SimpleLock> newLock = lock.get().extend(Instant.now().plusSeconds(10), Instant.now());
        assertThat(newLock).isNotEmpty();

        // wait for the original lock to be released
        sleepFor(originalLockDuration);
        assertLocked(LOCK_NAME1);

        newLock.get().unlock();
        assertUnlocked(LOCK_NAME1);
    }

    @Test
    public void shouldNotBeAbleToExtendUnlockedLock() {
        Duration originalLockDuration = Duration.ofMillis(1_000);
        Optional<SimpleLock> lock = getLockProvider().lock(lockConfig(LOCK_NAME1, originalLockDuration, Duration.ZERO));
        assertThat(lock).isNotEmpty();
        assertLocked(LOCK_NAME1);
        lock.get().unlock();
        assertUnlocked(LOCK_NAME1);

        assertInvalidLock(() -> lock.get().extend(Instant.now().plusSeconds(10), Instant.now()));
    }

    @Test
    public void shouldNotBeAbleToExtendExpiredLock() {
        Optional<SimpleLock> lock = getLockProvider().lock(lockConfig(LOCK_NAME1, Duration.ofMillis(1), Duration.ZERO));
        sleepFor(Duration.ofMillis(1));
        assertThat(lock).isNotEmpty();

        Optional<SimpleLock> newLock = lock.get().extend(Instant.now().plusSeconds(10), Instant.now());
        assertThat(newLock).isEmpty();
        assertUnlocked(LOCK_NAME1);
    }


    @Test
    public void shouldBeAbleToExtendAtLeast() {
        Optional<SimpleLock> lock = getLockProvider().lock(lockConfig(LOCK_NAME1, Duration.ofSeconds(10), Duration.ZERO));
        assertThat(lock).isNotEmpty();

        Optional<SimpleLock> newLock = lock.get().extend(Instant.now().plusSeconds(10), Instant.now().plusSeconds(9));
        assertThat(newLock).isNotEmpty();
        newLock.get().unlock();
        assertLocked(LOCK_NAME1);
    }

    @Test
    public void lockCanNotBeExtendedTwice() {
        Optional<SimpleLock> lock = getLockProvider().lock(lockConfig(LOCK_NAME1, Duration.ofSeconds(10), Duration.ZERO));
        assertThat(lock).isNotEmpty();
        Optional<SimpleLock> newLock = lock.get().extend(Instant.now().plusSeconds(10), Instant.now().plusSeconds(9));
        assertThat(newLock).isNotEmpty();

        assertInvalidLock(() -> lock.get().extend(Instant.now().plusSeconds(10), Instant.now().plusSeconds(9)));
    }

    @Test
    public void lockCanNotBeUnlockedAfterExtending() {
        Optional<SimpleLock> lock = getLockProvider().lock(lockConfig(LOCK_NAME1, Duration.ofSeconds(10), Duration.ZERO));
        assertThat(lock).isNotEmpty();
        Optional<SimpleLock> newLock = lock.get().extend(Instant.now().plusSeconds(10), Instant.now().plusSeconds(9));
        assertThat(newLock).isNotEmpty();

        assertInvalidLock(() -> lock.get().unlock());
    }

    void assertInvalidLock(ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOf(IllegalStateException.class);
    }
}