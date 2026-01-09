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

import static net.javacrumbs.shedlock.core.LockAssert.assertLocked;
import static net.javacrumbs.shedlock.spring.TestUtils.hasParams;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RetryableMethodProxyAopTest.RetryableMethodProxyAopConfig.class)
public class RetryableMethodProxyAopTest {
    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private TestBean testBean;

    private final SimpleLock simpleLock = mock(SimpleLock.class);

    @BeforeEach
    public void prepareMocks() {
        Mockito.reset(lockProvider, simpleLock);
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLock));
        testBean.reset();
    }

    @Test
    public void shouldWorkWithRetryable() {
        testBean.retryable();
        verify(lockProvider, times(2)).lock(hasParams("retryable", 30_000, 0));
        verify(simpleLock, times(2)).unlock();
        assertThat(testBean.wasMethodCalled()).isTrue();
    }

    @Configuration
    @EnableScheduling
    @EnableResilientMethods
    @EnableSchedulerLock(defaultLockAtMostFor = "30s")
    public static class RetryableMethodProxyAopConfig {

        @Bean
        public LockProvider lockProvider() {
            return mock(LockProvider.class);
        }

        @Bean
        public TestBean testBean() {
            return new TestBean();
        }
    }

    static class TestBean {
        private final AtomicBoolean called = new AtomicBoolean(false);

        void reset() {
            called.set(false);
        }

        boolean wasMethodCalled() {
            return called.get();
        }

        @Retryable
        @SchedulerLock(name = "retryable")
        public void retryable() {
            if (!called.get()) {
                called.set(true);
                throw new RuntimeException("Please retry");
            }
            assertLocked();
        }
    }
}
