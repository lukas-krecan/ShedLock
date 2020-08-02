package net.javacrumbs.shedlock.provider.consul;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.kv.model.PutParams;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsulLockProviderTest {
    // lower values may produce false negatives because scheduler may not complete necessary tasks in time
    private static final Duration SMALL_MIN_TTL = Duration.ofMillis(200);
    private ConsulClient mockConsulClient = mock(ConsulClient.class);
    private ConsulLockProvider lockProvider = new ConsulLockProvider(mockConsulClient).setMinSessionTtl(SMALL_MIN_TTL);

    @BeforeEach
    void setUp() {
        lockProvider = new ConsulLockProvider(mockConsulClient);
        when(mockConsulClient.sessionCreate(any(), any())).thenReturn(new Response<>(UUID.randomUUID().toString(), null, null, null));
        when(mockConsulClient.setKVValue(any(), any(), any(PutParams.class))).thenReturn(new Response<>(true, null, null, null));
    }

    @Test
    void destroysSessionWithoutRenewalIfNoLockAtLeastForProvided() {
        Optional<SimpleLock> lock = lockProvider.lock(lockConfig("sam-bridges", SMALL_MIN_TTL, Duration.ZERO));
        assertThat(lock).isNotEmpty();
        lock.get().unlock();
        sleep(50);
        verify(mockConsulClient, never()).renewSession(anyString(), any());
        verify(mockConsulClient).sessionDestroy(anyString(), any());
    }

    @Test
    void destroysSessionAfterLockedForAtLeastFor() {
        Optional<SimpleLock> lock = lockProvider.lock(lockConfig("snake", SMALL_MIN_TTL, SMALL_MIN_TTL.dividedBy(2)));
        assertThat(lock).isNotEmpty();
        lock.get().unlock();
        sleep(SMALL_MIN_TTL.dividedBy(2).toMillis() + 10);
        verify(mockConsulClient).sessionDestroy(anyString(), any());
    }

    @Test
    void doesNotLockIfLockIsAlreadyObtained() {
        when(mockConsulClient.setKVValue(eq("naruto-leader"), any(), any(PutParams.class)))
            .thenReturn(new Response<>(false, null, null, null));

        Optional<SimpleLock> lock = lockProvider.lock(lockConfig("naruto", SMALL_MIN_TTL, SMALL_MIN_TTL.dividedBy(2)));
        assertThat(lock).isEmpty();
    }

    @Test
    void doesNotBlockSchedulerInCaseOfFailure() {
        when(mockConsulClient.sessionDestroy(any(), any()))
            .thenThrow(new RuntimeException("Sasuke is not in Konoha, Naruto alone is unable to destroy session :("))
            .thenReturn(new Response<>(null, null, null, null));

        Optional<SimpleLock> lock = lockProvider.lock(lockConfig("sasuke", SMALL_MIN_TTL.multipliedBy(10), SMALL_MIN_TTL));
        assertThat(lock).isNotEmpty();
        lock.get().unlock();
        sleep(SMALL_MIN_TTL.toMillis() + 10);

        Optional<SimpleLock> lock2 = lockProvider.lock(lockConfig("sakura", SMALL_MIN_TTL.multipliedBy(10), SMALL_MIN_TTL));
        assertThat(lock2).isNotEmpty();
        lock2.get().unlock();
        sleep(SMALL_MIN_TTL.toMillis() + 10);

        verify(mockConsulClient, times(2)).sessionDestroy(anyString(), any());
    }

    private LockConfiguration lockConfig(String name, Duration lockAtMostFor, Duration lockAtLeastFor) {
        return new LockConfiguration(ClockProvider.now(), name, lockAtMostFor, lockAtLeastFor);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
