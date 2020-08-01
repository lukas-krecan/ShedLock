package net.javacrumbs.shedlock.provider.consul;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.kv.model.GetValue;
import com.ecwid.consul.v1.kv.model.PutParams;
import com.ecwid.consul.v1.session.model.Session;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class ConsulSchedulableLockProviderTest {
    // lower values may produce false negatives because scheduler may not complete necessary tasks in time
    private static final Duration SESSION_TTL = Duration.ofMillis(200);
    private ConsulClient mockConsulClient = mock(ConsulClient.class);
    private ConsulSchedulableLockProvider lockProvider;

    @BeforeEach
    void setUp() {
        lockProvider = new ConsulSchedulableLockProvider(mockConsulClient);
        setSessionTtl(SESSION_TTL);
        when(mockConsulClient.getKVValue(anyString())).thenReturn(new Response<>(new GetValue(), null, null, null));
        when(mockConsulClient.sessionCreate(any(), any())).thenReturn(new Response<>(UUID.randomUUID().toString(), null, null, null));
        when(mockConsulClient.setKVValue(any(), any(), any(PutParams.class))).thenReturn(new Response<>(true, null, null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT5S", "P2D"})
    void throwsExceptionIfSessionTtlIsIncorrect(Duration sessionTtl) {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(
            () -> lockProvider.setSessionTtl(sessionTtl)
        ).withMessage("Session TTL must be from PT10S to PT24H");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT10S", "PT1M", "P1D"})
    void doesNotThrowExceptionIfSessionTtlConformsConsulLimits(Duration sessionTtl) {
        assertThatCode(() -> lockProvider.setSessionTtl(sessionTtl)).doesNotThrowAnyException();
    }

    @Test
    void renewsSessionUntilLockAtMostFor() {
        lockProvider.lock(lockConfig("lock", SESSION_TTL.multipliedBy(4), Duration.ZERO));
        sleep(SESSION_TTL.multipliedBy(4).toMillis() + 10);
        verify(mockConsulClient, times(6)).renewSession(anyString(), any());
        verify(mockConsulClient).sessionDestroy(anyString(), any());
    }

    @Test
    void destroysSessionWithoutRenewalIfLockAtMostForIsLowerThanSessionTtl() {
        lockProvider.lock(lockConfig("lock", SESSION_TTL.dividedBy(2), Duration.ZERO));
        sleep(SESSION_TTL.toMillis() + 10);
        verify(mockConsulClient, never()).renewSession(anyString(), any());
        verify(mockConsulClient).sessionDestroy(anyString(), any());
    }

    @Test
    void destroysSessionWithoutRenewalIfNoLockAtLeastForProvided() {
        Optional<SimpleLock> lock = lockProvider.lock(lockConfig("sam-bridges", SESSION_TTL, Duration.ZERO));
        assertThat(lock).isNotEmpty();
        lock.get().unlock();
        sleep(50);
        verify(mockConsulClient, never()).renewSession(anyString(), any());
        verify(mockConsulClient).sessionDestroy(anyString(), any());
    }

    @Test
    void destroysSessionAfterLockedForAtLeastFor() {
        Optional<SimpleLock> lock = lockProvider.lock(lockConfig("snake", SESSION_TTL, SESSION_TTL.dividedBy(2)));
        assertThat(lock).isNotEmpty();
        lock.get().unlock();
        sleep(SESSION_TTL.dividedBy(2).toMillis() + 10);
        verify(mockConsulClient).renewSession(anyString(), any());
        verify(mockConsulClient).sessionDestroy(anyString(), any());
    }

    @Test
    void doesNotLockIfLockIsAlreadyObtained() {
        when(mockConsulClient.setKVValue(eq("naruto-leader"), any(), any(PutParams.class)))
            .thenReturn(new Response<>(false, null, null, null));

        Optional<SimpleLock> lock = lockProvider.lock(lockConfig("naruto", SESSION_TTL, SESSION_TTL.dividedBy(2)));
        assertThat(lock).isEmpty();
    }

    @Test
    void continuesToRenewEvenAfterFailure() {
        Response<Session> sessionResponse = new Response<>(mock(Session.class), null, null, null);
        when(mockConsulClient.renewSession(anyString(), any()))
            .thenReturn(sessionResponse)
            .thenThrow(new RuntimeException("Sasuke is not in konoha, unable to renew session :("))
            .thenReturn(sessionResponse, sessionResponse);

        lockProvider.lock(lockConfig("sasuke", SESSION_TTL.multipliedBy(3), Duration.ZERO));

        sleep(SESSION_TTL.multipliedBy(4).toMillis() + 10);
        verify(mockConsulClient, times(4)).renewSession(anyString(), any());
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

    private void setSessionTtl(Duration sessionTtl) {
        // as session TTL validation should be done on setter, reflection allows us to bypass it for testing purposes
        try {
            Field ttlField = ConsulSchedulableLockProvider.class.getDeclaredField("sessionTtl");
            ttlField.setAccessible(true);
            ttlField.set(lockProvider, sessionTtl);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
