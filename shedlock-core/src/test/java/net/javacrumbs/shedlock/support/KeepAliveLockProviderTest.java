package net.javacrumbs.shedlock.support;

import static java.time.Duration.ZERO;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeepAliveLockProviderTest {
    private Instant currentTime = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock clock = mock(Clock.class);
    private final ExtensibleLockProvider wrappedProvider = mock(ExtensibleLockProvider.class);
    private final DeterministicScheduler scheduler = new DeterministicScheduler();
    private final KeepAliveLockProvider provider = new KeepAliveLockProvider(wrappedProvider, scheduler, ofSeconds(1));
    private final LockConfiguration lockConfiguration =
            new LockConfiguration(currentTime, "lock", ofSeconds(3), ofSeconds(2));
    private final SimpleLock originalLock = mock(SimpleLock.class);

    @BeforeEach
    void setUpMock() {
        when(clock.instant()).thenAnswer(invocation -> currentTime);
        ClockProvider.setClock(clock);
        when(wrappedProvider.lock(lockConfiguration)).thenReturn(Optional.of(originalLock));
    }

    @AfterEach
    void resetClock() {
        ClockProvider.setClock(Clock.systemUTC());
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
    void shouldScheduleNextExtensionFromStartOfSlowExtension() {
        SimpleLock extendedLock = mock(SimpleLock.class);
        when(originalLock.extend(any(), any())).thenAnswer(invocation -> {
            tickMs(1_000);
            return Optional.of(extendedLock);
        });
        mockExtension(extendedLock, Optional.of(extendedLock));

        SimpleLock lock = provider.lock(lockConfiguration).orElseThrow();
        tickMs(1_500);
        verify(originalLock).extend(ofSeconds(3), ofMillis(500));
        tickMs(499);
        verifyNoInteractions(extendedLock);
        tickMs(1);
        verify(extendedLock).extend(ofSeconds(3), ZERO);
        lock.unlock();
    }

    @Test
    void shouldScheduleImmediatelyWhenSlowExtensionPassesNextExtensionTime() {
        SimpleLock extendedLock = mock(SimpleLock.class);
        when(originalLock.extend(any(), any())).thenAnswer(invocation -> {
            tickMs(2_000);
            return Optional.of(extendedLock);
        });
        mockExtension(extendedLock, Optional.of(extendedLock));

        SimpleLock lock = provider.lock(lockConfiguration).orElseThrow();
        tickMs(1_500);
        verify(originalLock).extend(ofSeconds(3), ofMillis(500));
        verifyNoInteractions(extendedLock);
        tickMs(1);
        verify(extendedLock).extend(ofSeconds(3), ZERO);
        lock.unlock();
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
    void shouldRetryAfterHalfOfRemainingIntervalAndResumeNormalExtensions() {
        SimpleLock extendedLock = mock(SimpleLock.class);
        when(originalLock.extend(any(), any()))
                .thenThrow(new IllegalStateException("DB connection lost"))
                .thenReturn(Optional.of(extendedLock));
        mockExtension(extendedLock, Optional.of(extendedLock));

        SimpleLock lock = provider.lock(lockConfiguration).orElseThrow();
        tickMs(1_500);
        verify(originalLock).extend(ofSeconds(3), ofMillis(500));
        tickMs(749);
        verifyNoMoreInteractions(originalLock);
        tickMs(1);
        verify(originalLock).extend(ofSeconds(3), ZERO);
        tickMs(1_499);
        verifyNoInteractions(extendedLock);
        tickMs(1);
        verify(extendedLock).extend(ofSeconds(3), ZERO);
        lock.unlock();
        verify(extendedLock).unlock();
        tickMs(10_000);
        verifyNoMoreInteractions(originalLock, extendedLock);
    }

    @Test
    void shouldPreserveLockAtLeastUntilWhenRetrying() {
        LockConfiguration config = new LockConfiguration(currentTime, "lock", ofSeconds(3), ofSeconds(3));
        when(wrappedProvider.lock(config)).thenReturn(Optional.of(originalLock));
        when(originalLock.extend(any(), any()))
                .thenThrow(new IllegalStateException("DB connection lost"))
                .thenReturn(Optional.of(originalLock));

        SimpleLock lock = provider.lock(config).orElseThrow();
        tickMs(1_500);
        verify(originalLock).extend(ofSeconds(3), ofMillis(1_500));
        tickMs(750);
        verify(originalLock).extend(ofSeconds(3), ofMillis(750));
        lock.unlock();
    }

    @Test
    void shouldStopRetryingAtOriginalExpiry() {
        when(originalLock.extend(any(), any())).thenThrow(new IllegalStateException("DB connection lost"));

        provider.lock(lockConfiguration);
        tickMs(3_000);
        verify(originalLock, atLeast(2)).extend(any(), any());
        tickMs(10_000);
        verifyNoMoreInteractions(originalLock);
    }

    @Test
    void shouldNotExtendAtExpiry() {
        provider.lock(lockConfiguration);
        currentTime = lockConfiguration.getLockAtMostUntil();
        scheduler.tick(1_500, MILLISECONDS);
        verifyNoInteractions(originalLock);
    }

    @Test
    void shouldCancelRetryOnUnlock() {
        when(originalLock.extend(any(), any())).thenThrow(new IllegalStateException("DB connection lost"));

        SimpleLock lock = provider.lock(lockConfiguration).orElseThrow();
        tickMs(1_500);
        verify(originalLock).extend(ofSeconds(3), ofMillis(500));
        lock.unlock();
        verify(originalLock).unlock();
        tickMs(10_000);
        verifyNoMoreInteractions(originalLock);
    }

    @Test
    void shouldKeepExtendingAfterExceptionWithRealScheduler() {
        ClockProvider.setClock(Clock.systemUTC());
        ScheduledExecutorService realScheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            KeepAliveLockProvider realProvider =
                    new KeepAliveLockProvider(wrappedProvider, realScheduler, ofMillis(100));
            LockConfiguration config = new LockConfiguration(now(), "flaky", ofSeconds(2), ZERO);
            when(wrappedProvider.lock(config)).thenReturn(Optional.of(originalLock));
            when(originalLock.extend(any(), any()))
                    .thenThrow(new IllegalStateException("DB connection lost"))
                    .thenReturn(Optional.of(originalLock));

            SimpleLock lock = realProvider.lock(config).orElseThrow();
            try {
                verify(originalLock, timeout(5_000).atLeast(2)).extend(ofSeconds(2), ZERO);
            } finally {
                lock.unlock();
            }
        } finally {
            realScheduler.shutdownNow();
        }
    }

    @Test
    void shouldFailForShortLockAtMostFor() {
        assertThatThrownBy(() -> provider.lock(new LockConfiguration(now(), "short", ofMillis(100), ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void tickMs(int i) {
        for (int elapsed = 0; elapsed < i; elapsed++) {
            currentTime = currentTime.plusMillis(1);
            scheduler.tick(1, MILLISECONDS);
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private void mockExtension(SimpleLock originalLock, Optional<SimpleLock> extendedLock) {
        when(originalLock.extend(any(Duration.class), any())).thenReturn(extendedLock);
    }
}
