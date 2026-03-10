/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.core;

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.javacrumbs.shedlock.core.LockingTaskExecutor.TaskResult;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class DefaultLockingTaskExecutorTest {
    private final LockProvider lockProvider = mock(LockProvider.class);
    private final DefaultLockingTaskExecutor executor = new DefaultLockingTaskExecutor(lockProvider);
    private final LockingTaskExecutorListener listener = mock(LockingTaskExecutorListener.class);
    private final DefaultLockingTaskExecutor executorWithListener =
            new DefaultLockingTaskExecutor(lockProvider, listener);
    private final LockConfiguration lockConfig =
            new LockConfiguration(now(), "test", Duration.ofSeconds(100), Duration.ZERO);
    private final LockConfiguration lockConfig2 =
            new LockConfiguration(now(), "test2", Duration.ofSeconds(100), Duration.ZERO);

    @Test
    void lockShouldBeReentrant() {
        mockLockFor(lockConfig);

        AtomicBoolean called = new AtomicBoolean(false);

        executor.executeWithLock(
                (Runnable) () -> executor.executeWithLock((Runnable) () -> called.set(true), lockConfig), lockConfig);

        assertThat(called.get()).isTrue();
    }

    @Test
    void lockShouldBeReentrantForMultipleDifferentLocks() {
        mockLockFor(lockConfig);
        mockLockFor(lockConfig2);

        AtomicBoolean called = new AtomicBoolean(false);

        executor.executeWithLock(
                (Runnable) () -> executor.executeWithLock((Runnable) () -> called.set(true), lockConfig2), lockConfig);

        assertThat(called.get()).isTrue();
    }

    private void mockLockFor(LockConfiguration lockConfig2) {
        when(lockProvider.lock(lockConfig2)).thenReturn(Optional.of(mock(SimpleLock.class)));
    }

    @Test
    void shouldExecuteWithResult() throws Throwable {
        mockLockFor(lockConfig);

        TaskResult<String> result = executor.executeWithLock(() -> "result", lockConfig);
        assertThat(result.wasExecuted()).isTrue();
        assertThat(result.getResult()).isEqualTo("result");
    }

    @Test
    void shouldNotifyListenerWhenLockAcquiredAndTaskSucceeds() {
        SimpleLock lock = mock(SimpleLock.class);
        when(lockProvider.lock(lockConfig)).thenReturn(Optional.of(lock));

        executorWithListener.executeWithLock((Runnable) () -> {}, lockConfig);

        InOrder inOrder = inOrder(listener, lock);
        inOrder.verify(listener).onLockAttempt(lockConfig);
        inOrder.verify(listener).onLockAcquired(lockConfig);
        inOrder.verify(listener).onTaskStarted(lockConfig);
        inOrder.verify(listener).onTaskFinished(eq(lockConfig), any());
        inOrder.verify(lock).unlock();
    }

    @Test
    void shouldNotifyListenerWhenLockNotAcquired() {
        when(lockProvider.lock(lockConfig)).thenReturn(Optional.empty());
        AtomicBoolean called = new AtomicBoolean(false);

        executorWithListener.executeWithLock((Runnable) () -> called.set(true), lockConfig);

        verify(listener).onLockAttempt(lockConfig);
        verify(listener).onLockNotAcquired(lockConfig);
        verify(listener, never()).onLockAcquired(lockConfig);
        verify(listener, never()).onTaskStarted(lockConfig);
        verify(listener, never()).onTaskFinished(any(), any());
        assertThat(called.get()).isFalse();
    }

    @Test
    void shouldNotifyListenerForReentrantExecutionOfSameLockWithoutSecondLockAttempt() {
        SimpleLock lock = mock(SimpleLock.class);
        when(lockProvider.lock(lockConfig)).thenReturn(Optional.of(lock));

        executorWithListener.executeWithLock(
                (Runnable) () -> executorWithListener.executeWithLock((Runnable) () -> {}, lockConfig), lockConfig);

        verify(listener).onLockAttempt(lockConfig);
        verify(listener).onLockAcquired(lockConfig);
        verify(listener, times(2)).onTaskStarted(lockConfig);
        verify(listener, times(2)).onTaskFinished(eq(lockConfig), any());
        verify(lock).unlock();
    }

    @Test
    void shouldNotifyListenerForReentrantExecutionOfDifferentLocks() {
        SimpleLock outerLock = mock(SimpleLock.class);
        SimpleLock innerLock = mock(SimpleLock.class);
        when(lockProvider.lock(lockConfig)).thenReturn(Optional.of(outerLock));
        when(lockProvider.lock(lockConfig2)).thenReturn(Optional.of(innerLock));

        executorWithListener.executeWithLock(
                (Runnable) () -> executorWithListener.executeWithLock((Runnable) () -> {}, lockConfig2), lockConfig);

        InOrder inOrder = inOrder(listener, outerLock, innerLock);
        inOrder.verify(listener).onLockAttempt(lockConfig);
        inOrder.verify(listener).onLockAcquired(lockConfig);
        inOrder.verify(listener).onTaskStarted(lockConfig);
        inOrder.verify(listener).onLockAttempt(lockConfig2);
        inOrder.verify(listener).onLockAcquired(lockConfig2);
        inOrder.verify(listener).onTaskStarted(lockConfig2);
        inOrder.verify(listener).onTaskFinished(eq(lockConfig2), any());
        inOrder.verify(innerLock).unlock();
        inOrder.verify(listener).onTaskFinished(eq(lockConfig), any());
        inOrder.verify(outerLock).unlock();
    }

    @Test
    void shouldNotifyListenerWhenTaskThrows() {
        SimpleLock lock = mock(SimpleLock.class);
        when(lockProvider.lock(lockConfig)).thenReturn(Optional.of(lock));

        assertThatThrownBy(() -> executorWithListener.executeWithLock(
                        (Runnable) () -> {
                            throw new RuntimeException("task failed");
                        },
                        lockConfig))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("task failed");

        InOrder inOrder = inOrder(listener, lock);
        inOrder.verify(listener).onLockAttempt(lockConfig);
        inOrder.verify(listener).onLockAcquired(lockConfig);
        inOrder.verify(listener).onTaskStarted(lockConfig);
        inOrder.verify(listener).onTaskFinished(eq(lockConfig), any());
        inOrder.verify(lock).unlock();
    }

    @Test
    void shouldIgnoreListenerFailureDuringLockAttempt() {
        SimpleLock lock = mock(SimpleLock.class);
        doThrow(new RuntimeException("listener failed")).when(listener).onLockAttempt(lockConfig);
        when(lockProvider.lock(lockConfig)).thenReturn(Optional.of(lock));

        executorWithListener.executeWithLock((Runnable) () -> {}, lockConfig);

        verify(listener).onLockAttempt(lockConfig);
        verify(listener).onLockAcquired(lockConfig);
        verify(listener).onTaskStarted(lockConfig);
        verify(listener).onTaskFinished(eq(lockConfig), any());
        verify(lock).unlock();
    }

    @Test
    void shouldIgnoreListenerFailureDuringTaskFinished() {
        SimpleLock lock = mock(SimpleLock.class);
        doThrow(new RuntimeException("listener failed")).when(listener).onTaskFinished(eq(lockConfig), any());
        when(lockProvider.lock(lockConfig)).thenReturn(Optional.of(lock));

        executorWithListener.executeWithLock((Runnable) () -> {}, lockConfig);

        verify(listener).onLockAttempt(lockConfig);
        verify(listener).onLockAcquired(lockConfig);
        verify(listener).onTaskStarted(lockConfig);
        verify(listener).onTaskFinished(eq(lockConfig), any());
        verify(lock).unlock();
    }
}
