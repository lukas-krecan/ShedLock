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
package net.javacrumbs.shedlock.provider.consul;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
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
    private final ConsulClient mockConsulClient = mock(ConsulClient.class);
    private final ConsulLockProvider lockProvider = new ConsulLockProvider(mockConsulClient, SMALL_MIN_TTL);

    @BeforeEach
    void setUp() {
        when(mockConsulClient.sessionCreate(any(), any(), any())).thenReturn(new Response<>(UUID.randomUUID().toString(), null, null, null));
        mockLock(any(), true);
    }

    @Test
    void destroysSessionWithoutRenewalIfNoLockAtLeastForProvided() {
        Optional<SimpleLock> lock = lockProvider.lock(lockConfig("sam-bridges", SMALL_MIN_TTL, Duration.ZERO));
        assertThat(lock).isNotEmpty();
        lock.get().unlock();
        sleep(50);
        verify(mockConsulClient, never()).renewSession(anyString(), any());
        verify(mockConsulClient).sessionDestroy(anyString(), any(), any());
    }

    @Test
    void destroysSessionAfterLockedForAtLeastFor() {
        Optional<SimpleLock> lock = lockProvider.lock(lockConfig("snake", SMALL_MIN_TTL, SMALL_MIN_TTL.dividedBy(2)));
        assertThat(lock).isNotEmpty();
        lock.get().unlock();
        sleep(SMALL_MIN_TTL.dividedBy(2).toMillis() + 10);
        verify(mockConsulClient).sessionDestroy(anyString(), any(), any());
    }

    @Test
    void doesNotLockIfLockIsAlreadyObtained() {
        mockLock(eq("naruto-leader"), false);

        Optional<SimpleLock> lock = lockProvider.lock(lockConfig("naruto", SMALL_MIN_TTL, SMALL_MIN_TTL.dividedBy(2)));
        assertThat(lock).isEmpty();
    }

    @Test
    void destroysSessionIfLockIsAlreadyObtained() {
        mockLock(eq("naruto-leader"), false);

        Optional<SimpleLock> lock = lockProvider.lock(lockConfig("naruto", SMALL_MIN_TTL, SMALL_MIN_TTL.dividedBy(2)));
        assertThat(lock).isEmpty();
        verify(mockConsulClient).sessionDestroy(any(), eq(QueryParams.DEFAULT), any());
    }

    @Test
    void doesNotBlockSchedulerInCaseOfFailure() {
        when(mockConsulClient.sessionDestroy(any(), any(), any()))
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

        verify(mockConsulClient, times(2)).sessionDestroy(anyString(), any(), any());
    }

    private void mockLock(String eq, boolean b) {
        when(mockConsulClient.setKVValue(eq, any(), any(), any(PutParams.class)))
            .thenReturn(new Response<>(b, null, null, null));
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
