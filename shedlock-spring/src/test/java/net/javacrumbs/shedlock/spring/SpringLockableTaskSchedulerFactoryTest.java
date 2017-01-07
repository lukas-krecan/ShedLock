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