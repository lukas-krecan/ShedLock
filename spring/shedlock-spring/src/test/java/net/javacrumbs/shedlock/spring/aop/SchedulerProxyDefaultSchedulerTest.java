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
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock.InterceptMode.PROXY_SCHEDULER;
import static org.mockito.Mockito.mock;


/**
 * Test creation of default task scheduler
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SchedulerProxyDefaultSchedulerTest.SchedulerWrapperConfig.class)
public class SchedulerProxyDefaultSchedulerTest extends AbstractSchedulerProxyTest {

    @Override
    protected void assertRightSchedulerUsed() {

    }

    @Configuration
    @EnableScheduling
    @EnableSchedulerLock(defaultLockAtMostFor = "${default.lock_at_most_for}", defaultLockAtLeastFor = "${default.lock_at_least_for}", interceptMode = PROXY_SCHEDULER)
    @PropertySource("test.properties")
    static class SchedulerWrapperConfig {

        @Bean
        public LockProvider lockProvider() {
            return mock(LockProvider.class);
        }

        @Bean
        public ScheduledExecutorService feedUpdateScheduler(@Value("${thread-pool.size:10}") int threadPoolSize) {
            return new ScheduledThreadPoolExecutor(threadPoolSize);
        }
    }
}
