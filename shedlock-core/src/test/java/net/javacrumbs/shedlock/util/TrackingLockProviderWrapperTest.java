package net.javacrumbs.shedlock.util;

import static java.time.Duration.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;

class TrackingLockProviderWrapperTest {
    private final LockProvider wrapped = mock(LockProvider.class);
    private final TrackingLockProviderWrapper wrapper = new TrackingLockProviderWrapper(wrapped);

    @Test
    void shouldTrackLocks() {
        LockConfiguration configA = config("a");
        LockConfiguration configB = config("b");
        LockConfiguration configC = config("c");
        SimpleLock lockA = mock(SimpleLock.class);
        SimpleLock lockB = mock(SimpleLock.class);

        when(wrapped.lock(eq(configA))).thenReturn(Optional.of(lockA));
        when(wrapped.lock(eq(configB))).thenReturn(Optional.of(lockB));
        when(wrapped.lock(eq(configC))).thenReturn(Optional.empty());

        Optional<SimpleLock> a = wrapper.lock(configA);
        assertThat(a).isPresent();
        assertThat(((SimpleLockWithConfiguration) a.get()).getLockConfiguration())
                .isEqualTo(configA);

        Optional<SimpleLock> b = wrapper.lock(configB);
        assertThat(b).isPresent();

        assertThat(wrapper.lock(configC)).isNotPresent();

        assertThat(wrapper.getActiveLocks()).containsExactlyInAnyOrder(a.get(), b.get());

        a.get().unlock();
        b.get().extend(Duration.ofSeconds(10), ZERO);

        Collection<SimpleLock> activeLocks = wrapper.getActiveLocks();
        assertThat(activeLocks).containsExactlyInAnyOrder(b.get());
        activeLocks.forEach(SimpleLock::unlock);

        assertThat(wrapper.getActiveLocks()).isEmpty();

        verify(lockA).unlock();
        verify(lockB).unlock();
        verify(lockB).extend(Duration.ofSeconds(10), ZERO);
    }

    @Test
    void shouldUnlockOnlyOnce() {
        LockConfiguration configA = config("a");
        SimpleLock lockA = mock(SimpleLock.class);

        when(wrapped.lock(eq(configA))).thenReturn(Optional.of(lockA));

        Optional<SimpleLock> a = wrapper.lock(configA);
        assertThat(a).isPresent();

        Collection<SimpleLock> activeLocks = wrapper.getActiveLocks();
        assertThat(activeLocks).containsExactlyInAnyOrder(a.get());

        a.get().unlock();
        a.get().unlock();

        verify(lockA, times(1)).unlock();
    }

    private LockConfiguration config(String name) {
        return new LockConfiguration(Instant.now(), name, Duration.ofSeconds(10), ZERO);
    }
}
