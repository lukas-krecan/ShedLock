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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutorListener;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.aop.MethodProxyAopWithListenerConfig.TaskBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MethodProxyAopWithListenerConfig.class)
public class MethodProxyAopListenerTest {

    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private LockingTaskExecutorListener listener;

    @Autowired
    private TaskBean taskBean;

    private final SimpleLock simpleLock = Mockito.mock(SimpleLock.class);

    @BeforeEach
    public void prepareMocks() {
        Mockito.reset(lockProvider, listener, simpleLock);
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLock));
    }

    @Test
    public void shouldCallListenerOnLockAcquired() {
        taskBean.runTask();
        verify(listener).onLockAttempt(any());
        verify(listener).onLockAcquired(any());
        verify(listener).onTaskStarted(any());
        verify(listener).onTaskFinished(any(), any());
    }

    @Test
    public void shouldCallOnLockNotAcquiredWhenLockUnavailable() {
        when(lockProvider.lock(any())).thenReturn(Optional.empty());
        taskBean.runTask();
        verify(listener).onLockAttempt(any());
        verify(listener).onLockNotAcquired(any());
        verifyNoMoreInteractions(listener);
    }
}
