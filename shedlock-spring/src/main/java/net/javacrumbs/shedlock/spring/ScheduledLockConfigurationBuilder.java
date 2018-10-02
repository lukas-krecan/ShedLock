/**
 * Copyright 2009-2018 the original author or authors.
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

import net.javacrumbs.shedlock.core.LockProvider;
import org.springframework.scheduling.TaskScheduler;

import java.time.temporal.TemporalAmount;
import java.util.concurrent.ScheduledExecutorService;


/**
 * Builds configuration for ScheduledLock. Standard usage is following:
 * <p>
 * * <pre>
 * {@code
 *   {@literal @}Bean
 *   public ScheduledLockConfiguration taskScheduler(LockProvider lockProvider) {
 *       return ScheduledLockConfigurationBuilder
 *           .withLockProvider(lockProvider)
 *           .withPoolSize(10)
 *           .withDefaultLockAtMostFor(Duration.ofMinutes(10))
 *           .build();
 *   }
 * }
 * </pre>
 */
public interface ScheduledLockConfigurationBuilder {

    /**
     * Configures required lockProvider.
     */
    static ScheduledLockConfigurationBuilderWithoutTaskScheduler withLockProvider(LockProvider lockProvider) {
        return new DefaultScheduledLockConfigurationBuilder(lockProvider);
    }


    interface ScheduledLockConfigurationBuilderWithoutTaskScheduler {
        /**
         * Will use ThreadPoolTaskScheduler with given poolSize to execute the scheduled tasks.
         */
        ScheduledLockConfigurationBuilderWithoutDefaultLockAtMostFor withPoolSize(int poolSize);

        /**
         * Will use scheduledExecutorService for task execution.
         */
        ScheduledLockConfigurationBuilderWithoutDefaultLockAtMostFor withExecutorService(ScheduledExecutorService scheduledExecutorService);

        /**
         * Will use taskScheduler for task execution.
         */
        ScheduledLockConfigurationBuilderWithoutDefaultLockAtMostFor withTaskScheduler(TaskScheduler taskScheduler);
    }

    interface ScheduledLockConfigurationBuilderWithoutDefaultLockAtMostFor {
        /**
         * Upper limit after which the lock is automatically released.
         */
        ConfiguredScheduledLockConfigurationBuilder withDefaultLockAtMostFor(TemporalAmount defaultLockAtMostFor);
    }

    interface ConfiguredScheduledLockConfigurationBuilder {
        /**
         * Every lock is hold at least for given amount of time.
         */
        ConfiguredScheduledLockConfigurationBuilder withDefaultLockAtLeastFor(TemporalAmount defaultLockAtLeastFor);

        /**
         * Builds the configuration.
         */
        ScheduledLockConfiguration build();
    }
}

