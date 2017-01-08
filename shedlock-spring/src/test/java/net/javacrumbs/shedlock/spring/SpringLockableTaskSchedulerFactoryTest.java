/**
 * Copyright 2009-2017 the original author or authors.
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
package net.javacrumbs.shedlock.spring;

import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.Test;

import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SpringLockableTaskSchedulerFactoryTest {


    private LockProvider lockProvider = mock(LockProvider.class);
    private Runnable task = mock(Runnable.class);

    @Test
    public void shouldWrapScheduledExecutorService() {
        ScheduledExecutorService scheduledExecutorService = mock(ScheduledExecutorService.class);
        LockableTaskScheduler lockableTaskScheduler = SpringLockableTaskSchedulerFactory.newLockableTaskScheduler(scheduledExecutorService, lockProvider);
        lockableTaskScheduler.schedule(task, new Date());
        verify(scheduledExecutorService).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

}