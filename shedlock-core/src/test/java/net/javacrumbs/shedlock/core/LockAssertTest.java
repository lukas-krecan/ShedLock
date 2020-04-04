package net.javacrumbs.shedlock.core;

import org.junit.jupiter.api.Test;

import java.util.Optional;

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
        LockConfiguration lockConfiguration = new LockConfiguration("test", ClockProvider.now().plusSeconds(10));

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
