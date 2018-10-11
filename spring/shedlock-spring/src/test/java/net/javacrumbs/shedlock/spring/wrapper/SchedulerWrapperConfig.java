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
package net.javacrumbs.shedlock.spring.wrapper;


import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock.InterceptMode.WRAP_SCHEDULER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

@Configuration
@EnableScheduling
@EnableSchedulerLock(mode = WRAP_SCHEDULER, defaultLockAtMostFor = "PT30S")
@PropertySource("test.properties")
public class SchedulerWrapperConfig {

    @Bean
    public LockProvider lockProvider() {
        return mock(LockProvider.class);
    }


    @Bean
    @SuppressWarnings("unchecked")
    public TaskScheduler testBean() {
        return new ThreadPoolTaskScheduler();
    }

}
