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
package net.javacrumbs.shedlock.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LockAssertTest {

    @Test
    void assertLockedShouldFailIfLockNotHeld() {
        assertThatThrownBy(LockAssert::assertLocked).hasMessageStartingWith("The task is not locked");
    }

    @Test
    void assertLockedShouldNotFailIfLockHeld() {
        LockConfiguration lockConfiguration = new LockConfiguration(now(), "test", Duration.ofSeconds(10), Duration.ZERO);

        LockProvider lockProvider = mock(LockProvider.class);
        when(lockProvider.lock(lockConfiguration)).thenReturn(Optional.of(mock(SimpleLock.class)));

        new DefaultLockingTaskExecutor(lockProvider).executeWithLock(
            (Runnable) LockAssert::assertLocked,
            lockConfiguration
        );
    }

    @Test
    void assertShouldNotFailIfConfiguredForTests() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
        LockAssert.assertLocked();

        LockAssert.TestHelper.makeAllAssertsPass(false);
        assertThatThrownBy(LockAssert::assertLocked).isInstanceOf(IllegalStateException.class);
    }
}
