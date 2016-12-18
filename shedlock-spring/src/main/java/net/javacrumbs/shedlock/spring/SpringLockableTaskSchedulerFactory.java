/**
 * Copyright 2009-2016 the original author or authors.
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

import net.javacrumbs.shedlock.core.DefaultLockManager;
import net.javacrumbs.shedlock.core.LockProvider;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Helper class to simplify configuration of Spring LockableTaskScheduler.
 */
public class SpringLockableTaskSchedulerFactory {

    /**
     * Wraps the task scheduler and ensures that {@link net.javacrumbs.shedlock.core.SchedulerLock} annotated methods
     * are locked using the lockProvider
     * @param taskScheduler wrapped task scheduler
     * @param lockProvider lock provider to be used
     */
    public static LockableTaskScheduler newLockableTaskScheduler(TaskScheduler taskScheduler, LockProvider lockProvider) {
        return new LockableTaskScheduler(taskScheduler, new DefaultLockManager(lockProvider, new SpringLockConfigurationExtractor()));
    }

    /**
     * Creates {@link ThreadPoolTaskScheduler} and ensures that {@link net.javacrumbs.shedlock.core.SchedulerLock} annotated methods
     * are locked using the lockProvider
     * @param poolSize size of the thread pool
     * @param lockProvider lock provider to be used
     */
    public static LockableTaskScheduler newLockableTaskScheduler(int poolSize, LockProvider lockProvider) {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(poolSize);
        taskScheduler.initialize();
        return newLockableTaskScheduler(taskScheduler, lockProvider);
    }
}
