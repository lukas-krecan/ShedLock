package net.javacrumbs.shedlock.core;

import net.javacrumbs.shedlock.core.LockingTaskExecutor.TaskResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultLockingTaskExecutorTest {
    private final LockProvider lockProvider = mock(LockProvider.class);
    private final DefaultLockingTaskExecutor executor = new DefaultLockingTaskExecutor(lockProvider);
    private final LockConfiguration lockConfig = new LockConfiguration("test", Instant.now().plusSeconds(100));

    @Test
    void lockShouldBeReentrant() {
        when(lockProvider.lock(lockConfig))
            .thenReturn(Optional.of(mock(SimpleLock.class)))
            .thenReturn(Optional.empty());

        AtomicBoolean called = new AtomicBoolean(false);

        executor.executeWithLock((Runnable) () -> executor.executeWithLock((Runnable) () -> called.set(true), lockConfig), lockConfig);

        assertThat(called.get()).isTrue();
    }

    @Test
    void shouldExecuteWithResult() throws Throwable {
        when(lockProvider.lock(lockConfig))
            .thenReturn(Optional.of(mock(SimpleLock.class)));

        TaskResult<String> result = executor.executeWithLock(() -> "result", lockConfig);
        assertThat(result.wasExecuted()).isTrue();
        assertThat(result.getResult()).isEqualTo("result");
    }
}
