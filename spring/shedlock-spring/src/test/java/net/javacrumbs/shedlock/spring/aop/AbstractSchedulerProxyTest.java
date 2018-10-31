/**
 * Copyright 2009-2018 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.spring.aop;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SchedulerLock;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static net.javacrumbs.shedlock.spring.TestUtils.hasParams;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;


@RunWith(SpringJUnit4ClassRunner.class)
public abstract class AbstractSchedulerProxyTest {
    @Autowired
    protected LockProvider lockProvider;

    @Autowired
    protected TaskScheduler taskScheduler;

    private final SimpleLock simpleLock = mock(SimpleLock.class);

    @Value("${default.lock_at_least_for}")
    private String defaultLockAtLeastFor;

    @Before
    public void prepareMocks() {
        Mockito.reset(lockProvider, simpleLock);
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLock));

    }

    protected abstract void assertRightSchedulerUsed();

    @Test
    public void shouldCallLockProviderOnSchedulerCall() throws NoSuchMethodException, ExecutionException, InterruptedException {
        Runnable task = task("annotatedMethod");
        taskScheduler.schedule(task, Instant.now()).get();
        verify(lockProvider).lock(hasParams("lockName", 30_000, getDefaultLockAtLeastFor()));
        verify(simpleLock).unlock();
    }


    @Test
    public void shouldUserPropertyName() throws NoSuchMethodException, ExecutionException, InterruptedException {
        Runnable task = task("spelMethod");
        taskScheduler.schedule(task, Instant.now()).get();
        verify(lockProvider).lock(hasParams("spel", 1000, 500));
        verify(simpleLock).unlock();
    }

    @Test
    public void shouldRethrowRuntimeException() throws NoSuchMethodException {
        Runnable task = task("throwsException");
        assertThatThrownBy(() -> schedule(task)).isInstanceOf(ExecutionException.class);
        verify(lockProvider).lock(hasParams("exception", 1_500, getDefaultLockAtLeastFor()));
        verify(simpleLock).unlock();
    }

    @Test
    public void shouldNotLockTaskExecutorMethods() {
        assertThat(taskScheduler).isInstanceOf(TaskExecutor.class);

        ((TaskExecutor)taskScheduler).execute(() -> {});
        verifyZeroInteractions(lockProvider);
    }

    private long getDefaultLockAtLeastFor() {
        return Duration.parse(defaultLockAtLeastFor).toMillis();
    }


    private Object schedule(Runnable task) throws InterruptedException, ExecutionException {
        return taskScheduler.schedule(task, Instant.now()).get();
    }

    private ScheduledMethodRunnable task(String methodName) throws NoSuchMethodException {
        return new ScheduledMethodRunnable(this, methodName);
    }

    @Test
    public void shouldNotLockProviderOnPureRunnable() throws ExecutionException, InterruptedException {
        taskScheduler.schedule(() -> { }, Instant.now()).get();
        verifyZeroInteractions(lockProvider);
    }

    @SchedulerLock(name = "lockName")
    public void annotatedMethod() {
        assertRightSchedulerUsed();
    }

    @SchedulerLock(name = "${property.value}", lockAtMostFor = 1000, lockAtLeastFor = 500)
    public void spelMethod() {

    }

    @SchedulerLock(name = "exception", lockAtMostFor = 1_500)
    public void throwsException() {
        throw new NullPointerException("Just for test");
    }

}
