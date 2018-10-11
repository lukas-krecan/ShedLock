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
package net.javacrumbs.shedlock.spring.wrapper;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SchedulerLock;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static net.javacrumbs.shedlock.spring.it.AbstractSchedulerTest.hasName;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SchedulerWrapperConfig.class)
public class SchedulerWrapperTest {
    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private TaskScheduler taskScheduler;

    private final SimpleLock simpleLock = mock(SimpleLock.class);

    @Before
    public void prepareMocks() {
        Mockito.reset(lockProvider, simpleLock);
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLock));

    }

    @Test
    public void shouldCallLockProviderOnSchedulerCall() throws NoSuchMethodException, ExecutionException, InterruptedException {
        Runnable task = task("annotatedMethod");
        taskScheduler.schedule(task, Instant.now()).get();
        verify(lockProvider).lock(hasName("lockName"));
        verify(simpleLock).unlock();
    }

    @Test
    public void shouldUserPropertyName() throws NoSuchMethodException, ExecutionException, InterruptedException {
        Runnable task = task("spelMethod");
        taskScheduler.schedule(task, Instant.now()).get();
        verify(lockProvider).lock(hasName("spel"));
        verify(simpleLock).unlock();
    }

    @Test
    public void shouldRethrowRuntimeException() throws NoSuchMethodException, ExecutionException, InterruptedException {
        Runnable task = task("throwsException");
        assertThatThrownBy(() -> schedule(task)).isInstanceOf(ExecutionException.class);
        verify(lockProvider).lock(hasName("exception"));
        verify(simpleLock).unlock();
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

    @SchedulerLock(name = "lockName", lockAtMostFor = 100)
    public void annotatedMethod() {

    }

    @SchedulerLock(name = "${property.value}", lockAtMostFor = 100)
    public void spelMethod() {

    }

    @SchedulerLock(name = "exception", lockAtMostFor = 100)
    public void throwsException() {
        throw new NullPointerException();
    }
}
