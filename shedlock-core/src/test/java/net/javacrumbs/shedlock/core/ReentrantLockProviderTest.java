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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReentrantLockProviderTest {
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(10);
    private final LockProvider lockProvider = new ReentrantLockProvider();
    private final LockConfigurationExtractor lockConfigurationExtractor = mock(LockConfigurationExtractor.class);
    private final LockManager lockManager = new DefaultLockManager(lockProvider, lockConfigurationExtractor);
    private final LockConfiguration configuration = new LockConfiguration(now(),"test", Duration.ofSeconds(60), Duration.ZERO);

    @BeforeEach
    void configureMocks() {
        when(lockConfigurationExtractor.getLockConfiguration(any(Runnable.class))).thenReturn(Optional.of(configuration));
    }

    @Test
    void shouldExecuteTask() throws ExecutionException, InterruptedException {
        AtomicBoolean executed = new AtomicBoolean(false);
        Runnable task = new LockableRunnable(() -> executed.set(true), lockManager);
        ScheduledFuture<?> scheduledFuture = executor.schedule(task, 1, TimeUnit.MILLISECONDS);
        scheduledFuture.get();
        assertThat(executed.get()).isEqualTo(true);
    }

    @Test
    void shouldNotExecuteTwiceAtTheSameTime() throws ExecutionException, InterruptedException {
        AtomicInteger executedTasks = new AtomicInteger();
        AtomicInteger runningTasks = new AtomicInteger();

        Runnable task = () -> {
            assertThat(runningTasks.getAndIncrement()).isEqualTo(0);
            sleep(50);
            assertThat(runningTasks.decrementAndGet()).isEqualTo(0);
            executedTasks.incrementAndGet();
        };
        ScheduledFuture<?> scheduledFuture1 = executor.schedule(new LockableRunnable(task, lockManager), 1, TimeUnit.MILLISECONDS);
        ScheduledFuture<?> scheduledFuture2 = executor.schedule(new LockableRunnable(task, lockManager), 1, TimeUnit.MILLISECONDS);
        scheduledFuture1.get();
        scheduledFuture2.get();

        // this assertion can fail if the tasks are scheduled one after another
        assertThat(executedTasks.get()).isEqualTo(1);
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
