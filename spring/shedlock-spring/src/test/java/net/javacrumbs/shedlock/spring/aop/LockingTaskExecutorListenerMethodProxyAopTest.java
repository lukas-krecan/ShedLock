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
package net.javacrumbs.shedlock.spring.aop;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutorListener;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
        classes = LockingTaskExecutorListenerMethodProxyAopTest.LockingTaskExecutorListenerAopConfig.class)
public class LockingTaskExecutorListenerMethodProxyAopTest {

    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private LockingTaskExecutorListener listener;

    @Autowired
    private TestBean testBean;

    private final SimpleLock simpleLock = mock(SimpleLock.class);

    @BeforeEach
    public void prepareMocks() {
        Mockito.reset(lockProvider, listener);
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLock));
    }

    @Test
    public void shouldCallListenerWhenLockAcquired() {
        testBean.lockedTask();

        verify(listener).onLockAttempt(any());
        verify(listener).onLockAcquired(any());
        verify(listener).onTaskStarted(any());
        verify(listener).onTaskFinished(any(), any());
        verify(listener, never()).onLockNotAcquired(any());
    }

    @Test
    public void shouldCallListenerWhenLockNotAcquired() {
        when(lockProvider.lock(any())).thenReturn(Optional.empty());

        testBean.lockedTask();

        verify(listener).onLockAttempt(any());
        verify(listener).onLockNotAcquired(any());
        verify(listener, never()).onLockAcquired(any());
        verify(listener, never()).onTaskStarted(any());
        verify(listener, never()).onTaskFinished(any(), any());
    }

    @Configuration
    @EnableScheduling
    @EnableSchedulerLock(defaultLockAtMostFor = "30s")
    public static class LockingTaskExecutorListenerAopConfig {

        @Bean
        public LockProvider lockProvider() {
            return mock(LockProvider.class);
        }

        @Bean
        public LockingTaskExecutorListener lockingTaskExecutorListener() {
            return mock(LockingTaskExecutorListener.class);
        }

        @Bean
        public TestBean testBean() {
            return new TestBean();
        }
    }

    static class TestBean {
        @SchedulerLock(name = "lockedTask")
        public void lockedTask() {}
    }
}
