package net.javacrumbs.shedlock.support;

import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static java.time.Duration.ZERO;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.time.Instant.now;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class KeepAliveLockProviderTest {
    private final ExtensibleLockProvider wrappedProvider = mock(ExtensibleLockProvider.class);
    private final DeterministicScheduler scheduler = new DeterministicScheduler();
    private final KeepAliveLockProvider provider = new KeepAliveLockProvider(wrappedProvider, scheduler, ofSeconds(1));
    private final LockConfiguration lockConfiguration = new LockConfiguration(now(), "lock", ofSeconds(3), ofSeconds(2));
    private final SimpleLock originalLock = mock(SimpleLock.class);

    @BeforeEach
    void setUpMock() {
        when(wrappedProvider.lock(lockConfiguration)).thenReturn(Optional.of(originalLock));
    }

    @Test
    void shouldScheduleKeepAliveTask() {
        mockExtension(originalLock, Optional.of(originalLock));
        Optional<SimpleLock> lock = provider.lock(lockConfiguration);
        assertThat(lock).isNotNull();
        tickMs(1_500);
        verify(originalLock).extend(lockConfiguration.getLockAtMostFor(), ofMillis(500));
        lock.get().unlock();
        verify(originalLock).unlock();
        tickMs(10_000);
        verifyNoMoreInteractions(originalLock);
    }

    @Test
    void shouldExtendMultipleTimes() {
        SimpleLock extendedLock = mock(SimpleLock.class);
        mockExtension(originalLock, Optional.of(extendedLock));
        mockExtension(extendedLock, Optional.of(extendedLock));

        Optional<SimpleLock> lock = provider.lock(lockConfiguration);
        assertThat(lock).isNotNull();
        tickMs(1_500);
        verify(originalLock).extend(lockConfiguration.getLockAtMostFor(), ofMillis(500));
        tickMs(1_500);
        verify(extendedLock).extend(lockConfiguration.getLockAtMostFor(), ZERO);
        lock.get().unlock();
        verify(extendedLock).unlock();
        tickMs(10_000);
        verifyNoMoreInteractions(originalLock);
    }

    @Test
    void shouldCancelIfCanNotExtend() {
        mockExtension(originalLock, Optional.empty());

        Optional<SimpleLock> lock = provider.lock(lockConfiguration);
        assertThat(lock).isNotNull();
        tickMs(1_500);
        verify(originalLock).extend(lockConfiguration.getLockAtMostFor(), ofMillis(500));
        tickMs(10_000);
        verifyNoMoreInteractions(originalLock);
    }

    @Test
    void shouldFailForShortLockAtMostFor() {
        assertThatThrownBy(() -> provider.lock(new LockConfiguration(now(), "short", ofMillis(100), ZERO)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private void tickMs(int i) {
        scheduler.tick(i, MILLISECONDS);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private void mockExtension(SimpleLock originalLock, Optional<SimpleLock> extendedLock) {
        when(originalLock.extend(any(Duration.class), any())).thenReturn(extendedLock);
    }
}
