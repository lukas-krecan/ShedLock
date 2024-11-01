/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.spring.aop;

import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicBoolean;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "60s")
public class MultipleLockProvidersMethodProxyAopConfig {

    @Bean
    public LockProvider lockProvider1() {
        return mock();
    }

    @Bean
    public LockProvider lockProvider2() {
        return mock();
    }

    @Bean
    TestBean testBean() {
        return new TestBean();
    }

    static class TestBean {
        private final AtomicBoolean called = new AtomicBoolean(false);

        void reset() {
            called.set(false);
        }

        boolean wasMethodCalled() {
            return called.get();
        }

        @SchedulerLock(name = "method1", lockProviderBeanName = "lockProvider1")
        public void method1() {
            called.set(true);
        }

        @SchedulerLock(name = "method2", lockProviderBeanName = "lockProvider2")
        public void method2() {
            called.set(true);
        }
    }
}
