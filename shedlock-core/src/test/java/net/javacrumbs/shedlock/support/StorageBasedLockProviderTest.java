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
package net.javacrumbs.shedlock.support;

import net.javacrumbs.shedlock.core.LockConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageBasedLockProviderTest {
    private static final LockConfiguration LOCK_CONFIGURATION = new LockConfiguration(now(),"name", Duration.of(5, ChronoUnit.MINUTES), Duration.ZERO);
    private static final LockException LOCK_EXCEPTION = new LockException("Test");

    private final StorageAccessor storageAccessor = mock(StorageAccessor.class);

    private final StorageBasedLockProvider lockProvider = new StorageBasedLockProvider(storageAccessor);

    @Test
    void newRecordShouldOnlyBeInserted() {
        when(storageAccessor.insertRecord(LOCK_CONFIGURATION)).thenReturn(true);
        assertThat(lockProvider.lock(LOCK_CONFIGURATION)).isNotEmpty();
        verify(storageAccessor, never()).updateRecord(LOCK_CONFIGURATION);

        // Should update directly without insert
        reset(storageAccessor);
        when(storageAccessor.updateRecord(LOCK_CONFIGURATION)).thenReturn(true);
        assertThat(lockProvider.lock(LOCK_CONFIGURATION)).isNotEmpty();
        verify(storageAccessor, never()).insertRecord(LOCK_CONFIGURATION);
        verify(storageAccessor).updateRecord(LOCK_CONFIGURATION);
    }

    @Test
    void updateOnDuplicateKey() {
        when(storageAccessor.insertRecord(LOCK_CONFIGURATION)).thenReturn(false);
        when(storageAccessor.updateRecord(LOCK_CONFIGURATION)).thenReturn(true);
        assertThat(lockProvider.lock(LOCK_CONFIGURATION)).isNotEmpty();
        verify(storageAccessor).updateRecord(LOCK_CONFIGURATION);

        // Should update directly without insert
        reset(storageAccessor);
        when(storageAccessor.updateRecord(LOCK_CONFIGURATION)).thenReturn(true);
        assertThat(lockProvider.lock(LOCK_CONFIGURATION)).isNotEmpty();
        verify(storageAccessor, never()).insertRecord(LOCK_CONFIGURATION);
        verify(storageAccessor).updateRecord(LOCK_CONFIGURATION);
    }

    @Test
    void doNotReturnLockIfUpdatedZeroRows() {
        when(storageAccessor.insertRecord(LOCK_CONFIGURATION)).thenReturn(false);
        when(storageAccessor.updateRecord(LOCK_CONFIGURATION)).thenReturn(false);
        assertThat(lockProvider.lock(LOCK_CONFIGURATION)).isEmpty();
    }

    @Test
    void shouldRethrowExceptionFromInsert() {
        when(storageAccessor.insertRecord(LOCK_CONFIGURATION)).thenThrow(LOCK_EXCEPTION);
        assertThatThrownBy(() -> lockProvider.lock(LOCK_CONFIGURATION)).isSameAs(LOCK_EXCEPTION);
    }

    @Test
    void shouldRethrowExceptionFromUpdate() {
        when(storageAccessor.insertRecord(LOCK_CONFIGURATION)).thenReturn(false);
        when(storageAccessor.updateRecord(LOCK_CONFIGURATION)).thenThrow(LOCK_EXCEPTION);
        assertThatThrownBy(() -> lockProvider.lock(LOCK_CONFIGURATION)).isSameAs(LOCK_EXCEPTION);
    }

    @Test
    void shouldNotCacheRecordIfUpdateFailed() {
        when(storageAccessor.insertRecord(LOCK_CONFIGURATION)).thenReturn(false);
        when(storageAccessor.updateRecord(LOCK_CONFIGURATION)).thenThrow(LOCK_EXCEPTION);
        assertThatThrownBy(() -> lockProvider.lock(LOCK_CONFIGURATION)).isSameAs(LOCK_EXCEPTION);
        assertThatThrownBy(() -> lockProvider.lock(LOCK_CONFIGURATION)).isSameAs(LOCK_EXCEPTION);
        verify(storageAccessor, times(2)).insertRecord(LOCK_CONFIGURATION);
    }
}
