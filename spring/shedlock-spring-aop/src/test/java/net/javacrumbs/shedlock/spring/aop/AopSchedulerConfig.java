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
package net.javacrumbs.shedlock.spring.aop;


import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import static net.javacrumbs.shedlock.spring.aop.AopConfig.DEFAULT_LOCK_AT_LEAST_FOR;
import static net.javacrumbs.shedlock.spring.aop.AopConfig.DEFAULT_LOCK_AT_MOST_FOR;
import static org.mockito.Mockito.mock;

@Configuration
@EnableScheduling
@EnableAspectJAutoProxy
public class AopSchedulerConfig {
    private static final Logger logger = LoggerFactory.getLogger(AopSchedulerTest.class);

    @Bean
    public LockProvider lockProvider() {
        return mock(LockProvider.class);
    }

    @Bean
    public ScheduledLockAopConfiguration scheduledLockAopConfiguration(LockProvider lockProvider) {
        return new ScheduledLockAopConfiguration(new DefaultLockingTaskExecutor(lockProvider), DEFAULT_LOCK_AT_MOST_FOR, DEFAULT_LOCK_AT_LEAST_FOR);
    }


    @SchedulerLock(name = "taskName")
    @Scheduled(fixedRate = 10)
    public void run() {
        logger.info("Task executed");
    }
}
