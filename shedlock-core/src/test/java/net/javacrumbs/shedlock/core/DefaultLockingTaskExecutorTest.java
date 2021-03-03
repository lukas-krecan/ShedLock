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
package net.javacrumbs.shedlock.core;

import net.javacrumbs.shedlock.core.LockingTaskExecutor.TaskResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultLockingTaskExecutorTest {
    private final LockProvider lockProvider = mock(LockProvider.class);
    private final DefaultLockingTaskExecutor executor = new DefaultLockingTaskExecutor(lockProvider);
    private final LockConfiguration lockConfig = new LockConfiguration(now(),"test", Duration.ofSeconds(100), Duration.ZERO);

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
