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
import org.junit.Test;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class CleanupTest {
    @Test
    public void shouldCloseTaskExecutor() throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class);
        context.start();
        LockableTaskScheduler taskScheduler = context.getBean(LockableTaskScheduler.class);
        assertThat(taskScheduler).isNotNull();
        context.close();


        verify(Config.taskScheduler).destroy();
    }

    @Configuration
    static class Config {
        interface DisposableTaskScheduler extends TaskScheduler, DisposableBean {};

        private static LockProvider lockProvider = mock(LockProvider.class);
        private static DisposableTaskScheduler taskScheduler = mock(DisposableTaskScheduler.class);

        @Bean
        public ScheduledLockConfiguration shedlockConfig() {
            return ScheduledLockConfigurationBuilder
                .withLockProvider(lockProvider)
                .withTaskScheduler(taskScheduler)
                .withDefaultLockAtMostFor(Duration.ofMinutes(10))
                .build();
        }
    }
}
