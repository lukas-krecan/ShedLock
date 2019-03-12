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
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;

import static net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock.InterceptMode.PROXY_METHOD;
import static org.mockito.Mockito.mock;

@Configuration
@EnableScheduling
@EnableSchedulerLock(mode = PROXY_METHOD, defaultLockAtMostFor = "${default.lock_at_most_for}", defaultLockAtLeastFor = "${default.lock_at_least_for}")
@PropertySource("test.properties")
public class MethodProxyAopConfig {

    @Bean
    public LockProvider lockProvider() {
        return mock(LockProvider.class);
    }


    @Bean
    public TestBean testBean() {
        return new TestBean();
    }

    @Bean
    public AnotherTestBean anotherTestBean() {
        return new AnotherTestBeanImpl();
    }

    static class TestBean {

        public void noAnnotation() {
        }

        @SchedulerLock(name = "normal")
        public void normal() {
        }

        @SchedulerLock(name = "runtimeException", lockAtMostFor = 100)
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

        @SchedulerLock(name = "${property.value}", lockAtLeastFor = 1_000)
        public void spel() {

        }
    }

    interface AnotherTestBean {
        void runManually();
    }

    static class AnotherTestBeanImpl implements AnotherTestBean {

        @Override
        @SchedulerLock(name = "classAnnotation")
        public void runManually() {

        }
    }
}
