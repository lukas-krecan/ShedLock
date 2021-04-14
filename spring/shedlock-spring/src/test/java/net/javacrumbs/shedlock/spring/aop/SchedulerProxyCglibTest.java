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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.Executors;

import static net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock.InterceptMode.PROXY_SCHEDULER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;


@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SchedulerProxyCglibTest.SchedulerWrapperConfig.class)
public class SchedulerProxyCglibTest extends AbstractSchedulerProxyTest {

    @Test
    public void shouldNotCreateDefaultScheduler() {
        assertThat(taskScheduler).isInstanceOf(MyTaskScheduler.class);
    }

    @Override
    protected void assertRightSchedulerUsed() {
        assertThat(Thread.currentThread().getName()).startsWith("my-thread");
    }

    @Configuration
    @EnableScheduling
    @EnableSchedulerLock(defaultLockAtMostFor = "${default.lock_at_most_for}", defaultLockAtLeastFor = "${default.lock_at_least_for}", proxyTargetClass = true, interceptMode = PROXY_SCHEDULER)
    @PropertySource("test.properties")
    static class SchedulerWrapperConfig {

        @Bean
        public LockProvider lockProvider() {
            return mock(LockProvider.class);
        }


        @Bean
        public TaskScheduler taskScheduler() {
            return new MyTaskScheduler();
        }
    }

    interface MyInterface {
    }

    private static class MyTaskScheduler extends ConcurrentTaskScheduler implements MyInterface {
        MyTaskScheduler() {
            super(Executors.newScheduledThreadPool(10, new CustomizableThreadFactory("my-thread")));
        }
    }
}
