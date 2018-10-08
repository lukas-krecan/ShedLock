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
package net.javacrumbs.shedlock.spring.aop;


import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.mockito.Mockito.mock;

@Configuration
@EnableScheduling
@EnableAspectJAutoProxy
@PropertySource("test.properties")
public class AopConfig {
    static final Duration DEFAULT_LOCK_AT_MOST_FOR = Duration.of(30, ChronoUnit.MINUTES);
    static final Duration DEFAULT_LOCK_AT_LEAST_FOR = Duration.of(5, ChronoUnit.MILLIS);

    @Bean
    public LockProvider lockProvider() {
        return mock(LockProvider.class);
    }

    @Bean
    public ScheduledLockConfiguration scheduledLockAopConfiguration(LockProvider lockProvider) {
        return ScheduledLockConfigurationBuilder
              .withLockProvider(lockProvider)
              .withDefaultLockAtMostFor(DEFAULT_LOCK_AT_MOST_FOR)
              .withDefaultLockAtLeastFor(DEFAULT_LOCK_AT_LEAST_FOR)
              .build();
    }

    @Bean
    public TestBean testBean() {
        return new TestBean();
    }

    static class TestBean {

        public void noAnnotation() {
        }

        @SchedulerLock(name = "normal")
        public void normal() {
        }

        @SchedulerLock(name = "runtimeException")
        public Void throwsRuntimeException() {
            throw new RuntimeException();
        }

        @SchedulerLock(name = "exception")
        public void throwsException() throws Exception {
            throw new IOException();
        }

        @SchedulerLock(name = "returnsValue")
        public int returnsValue() {
            return 0;
        }

        @SchedulerLock(name = "${property.value}")
        public void spel() {

        }
    }
}
