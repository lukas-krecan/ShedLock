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
package net.javacrumbs.shedlock.spring.aop;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.ExtendedLockConfigurationExtractor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static net.javacrumbs.shedlock.spring.TestUtils.hasParams;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


@ExtendWith(SpringExtension.class)
public abstract class AbstractSchedulerProxyTest {
    @Autowired
    protected LockProvider lockProvider;

    @Autowired
    protected TaskScheduler taskScheduler;

    @Autowired
    private ExtendedLockConfigurationExtractor extractor;

    private final SimpleLock simpleLock = mock(SimpleLock.class);

    @Value("${default.lock_at_least_for}")
    private String defaultLockAtLeastFor;

    @BeforeEach
    public void prepareMocks() {
        Mockito.reset(lockProvider, simpleLock);
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLock));

    }

    protected abstract void assertRightSchedulerUsed();

    @Test
    public void shouldCallLockProviderOnSchedulerCall() throws NoSuchMethodException, ExecutionException, InterruptedException {
        Runnable task = task("annotatedMethod");
        taskScheduler.schedule(task, now()).get();
        verify(lockProvider).lock(hasParams("lockName", 30_000, getDefaultLockAtLeastFor()));
        verify(simpleLock).unlock();
    }

    @Test
    public void shouldCallLockProviderOnSchedulerCallDeprecatedAnnotation() throws NoSuchMethodException, ExecutionException, InterruptedException {
        Runnable task = task("oldMethod");
        taskScheduler.schedule(task, now()).get();
        verify(lockProvider).lock(hasParams("lockName", 30_000, getDefaultLockAtLeastFor()));
        verify(simpleLock).unlock();
    }

    @Test
    public void shouldUseCustomAnnotation() throws NoSuchMethodException, ExecutionException, InterruptedException {
        Runnable task = task("custom");
        taskScheduler.schedule(task, now()).get();
        verify(lockProvider).lock(hasParams("custom", 60_000, 1_000));
        verify(simpleLock).unlock();
    }


    @Test
    public void shouldUserPropertyName() throws NoSuchMethodException, ExecutionException, InterruptedException {
        Runnable task = task("spelMethod");
        taskScheduler.schedule(task, now()).get();
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
        verifyNoInteractions(lockProvider);
    }

    private long getDefaultLockAtLeastFor() {
        return StringToDurationConverter.INSTANCE.convert(defaultLockAtLeastFor).toMillis();
    }


    private void schedule(Runnable task) throws InterruptedException, ExecutionException {
        taskScheduler.schedule(task, now()).get();
    }

    private ScheduledMethodRunnable task(String methodName) throws NoSuchMethodException {
        return new ScheduledMethodRunnable(this, methodName);
    }

    @Test
    public void shouldNotLockProviderOnPureRunnable() throws ExecutionException, InterruptedException {
        taskScheduler.schedule(() -> { }, now()).get();
        verifyNoInteractions(lockProvider);
    }

    @Test
    public void extractorShouldBeDefined() {
        assertThat(extractor).isNotNull();
    }

    @net.javacrumbs.shedlock.core.SchedulerLock(name = "lockName")
    public void oldMethod() {
        assertRightSchedulerUsed();
    }

    @SchedulerLock(name = "lockName")
    public void annotatedMethod() {
        assertRightSchedulerUsed();
    }

    @MyScheduled(name = "custom", lockAtMostFor = "1m", lockAtLeastFor = "1s")
    public void custom() {
        assertRightSchedulerUsed();
    }

    @SchedulerLock(name = "${property.value}", lockAtMostFor = "1000", lockAtLeastFor = "500")
    public void spelMethod() {

    }

    @SchedulerLock(name = "exception", lockAtMostFor = "1500")
    public void throwsException() {
        throw new NullPointerException("Just for test");
    }

}
