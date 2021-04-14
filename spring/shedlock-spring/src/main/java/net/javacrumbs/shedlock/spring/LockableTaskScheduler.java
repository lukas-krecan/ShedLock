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
package net.javacrumbs.shedlock.spring;

import net.javacrumbs.shedlock.core.LockManager;
import net.javacrumbs.shedlock.core.LockableRunnable;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;

import static java.util.Objects.requireNonNull;

/**
 * Wraps a all tasks to {@link LockableRunnable} and delegates all calls to a {@link TaskScheduler}.
 */
public class LockableTaskScheduler implements TaskScheduler, DisposableBean {
    private final TaskScheduler taskScheduler;
    private final LockManager lockManager;

    public LockableTaskScheduler(@NonNull TaskScheduler taskScheduler, @NonNull LockManager lockManager) {
        this.taskScheduler = requireNonNull(taskScheduler);
        this.lockManager = requireNonNull(lockManager);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
        return taskScheduler.schedule(wrap(task), trigger);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Date startTime) {
        return taskScheduler.schedule(wrap(task), startTime);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Date startTime, long period) {
        return taskScheduler.scheduleAtFixedRate(wrap(task), startTime, period);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long period) {
        return taskScheduler.scheduleAtFixedRate(wrap(task), period);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Date startTime, long delay) {
        return taskScheduler.scheduleWithFixedDelay(wrap(task), startTime, delay);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long delay) {
        return taskScheduler.scheduleWithFixedDelay(wrap(task), delay);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
        return taskScheduler.schedule(wrap(task), startTime);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
        return taskScheduler.scheduleAtFixedRate(wrap(task), startTime, period);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
        return taskScheduler.scheduleAtFixedRate(wrap(task), period);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
        return taskScheduler.scheduleWithFixedDelay(wrap(task), startTime, delay);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
        return taskScheduler.scheduleWithFixedDelay(wrap(task), delay);
    }

    private Runnable wrap(Runnable task) {
        return new LockableRunnable(task, lockManager);
    }

    @Override
    public void destroy() throws Exception {
        if (taskScheduler instanceof DisposableBean) {
            ((DisposableBean) taskScheduler).destroy();
        }
    }
}
