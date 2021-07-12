package net.javacrumbs.shedlock.core;

import net.javacrumbs.shedlock.core.LockExtender.LockCanNotBeExtendedException;
import net.javacrumbs.shedlock.core.LockExtender.NoActiveLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static java.time.Duration.ZERO;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LockExtenderTest {
    private final LockProvider lockProvider = mock(LockProvider.class);
    private final SimpleLock lock = mock(SimpleLock.class);
    private final  SimpleLock newLock = mock(SimpleLock.class);
    private final DefaultLockingTaskExecutor executor = new DefaultLockingTaskExecutor(lockProvider);
    private final LockConfiguration configuration = new LockConfiguration(Instant.now(), "test", ofSeconds(1), ZERO);
    private final Duration extendBy = ofSeconds(1);

    @BeforeEach
    void configureLockProvider() {
        when(lockProvider.lock(any())).thenReturn(Optional.of(lock));
    }

    @Test
    void shouldExtendActiveLock() {
        when(lock.extend(extendBy, ZERO)).thenReturn(Optional.of(newLock));

        Runnable task = () -> LockExtender.extendActiveLock(extendBy, ZERO);
        executor.executeWithLock(task, configuration);

        verify(lock).extend(extendBy, ZERO);
    }

    @Test
    void shouldExtendActiveLockTwice() {
        when(lock.extend(extendBy, ZERO)).thenReturn(Optional.of(newLock));
        when(newLock.extend(extendBy, ZERO)).thenReturn(Optional.of(mock(SimpleLock.class)));

        Runnable task = () -> {
            LockExtender.extendActiveLock(extendBy, ZERO);
            LockExtender.extendActiveLock(extendBy, ZERO);
        };

        executor.executeWithLock(task, configuration);

        verify(lock).extend(extendBy, ZERO);
        verify(newLock).extend(extendBy, ZERO);
    }

    @Test
    void shouldFailIfLockCanNotBeExtended() {
        when(lock.extend(extendBy, ZERO)).thenReturn(Optional.empty());

        Runnable task = () -> LockExtender.extendActiveLock(extendBy, ZERO);

        assertThatThrownBy(() -> executor.executeWithLock(task, configuration))
            .isInstanceOf(LockCanNotBeExtendedException.class);
    }

    @Test
    void shouldFailIfNoActiveLock() {
        assertThatThrownBy(() -> LockExtender.extendActiveLock(ofSeconds(1), ofSeconds(0)))
            .isInstanceOf(NoActiveLockException.class);
    }
}
