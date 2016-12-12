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
package net.javacrumbs.shedlock.test.boot;

import net.javacrumbs.shedlock.core.DefaultLockManager;
import net.javacrumbs.shedlock.core.LockConfigurationExtractor;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.ReentrantLockProvider;
import net.javacrumbs.shedlock.spring.SpringLockConfigurationExtractor;
import net.javacrumbs.shedlock.spring.LockableTaskScheduler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@SpringBootApplication
@EnableScheduling
public class Application {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(Application.class);
    }


    @Bean
    public TaskScheduler taskScheduler(LockProvider lockProvider, LockConfigurationExtractor lockConfigurationExtractor) {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(10);
        taskScheduler.initialize();

        return new LockableTaskScheduler(taskScheduler, new DefaultLockManager(lockProvider, lockConfigurationExtractor));
    }

    @Bean
    public LockProvider lockProvider() {
        return new ReentrantLockProvider();
    }

    @Bean
    public LockConfigurationExtractor lockConfigurationExtractor() {
        return new SpringLockConfigurationExtractor();
    }
}
