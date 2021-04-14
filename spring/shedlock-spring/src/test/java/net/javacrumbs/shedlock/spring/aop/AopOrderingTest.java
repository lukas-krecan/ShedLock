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

import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;


@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {AopOrderingTest.AopOrderConfig.class, AopOrderingTest.Aspects.class})
public class AopOrderingTest {
    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private TestBean testBean;

    private final SimpleLock simpleLock = mock(SimpleLock.class);

    private static final List<String> aspectsCalled = new ArrayList<>();

    @BeforeEach
    public void prepareMocks() {
        Mockito.reset(lockProvider, simpleLock);
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLock));
        testBean.reset();
        aspectsCalled.clear();
    }

    @Test
    void shouldCallAspectsInTheRightOrder() {
        testBean.normalMethod();
        assertThat(aspectsCalled).containsExactly("first", "last");
        assertThat(testBean.wasMethodCalled());
    }

    @Configuration
    @EnableSchedulerLock(defaultLockAtMostFor = "1s", order = 100)
    static class AopOrderConfig {
        @Bean
        public LockProvider lockProvider() {
            return mock(LockProvider.class);
        }

        @Bean
        public TestBean testBean() {
            return new TestBean();
        }

    }

    @Configuration
    static class Aspects {
        @Bean
        @Role(ROLE_INFRASTRUCTURE)
        public Advisor firstAspect() {
            DefaultPointcutAdvisor aspect = new DefaultPointcutAdvisor(
                shedlockPointcut(),
                (MethodInterceptor) invocation -> {
                    aspectsCalled.add("first");
                    assertThatThrownBy(LockAssert::assertLocked).isInstanceOf(IllegalStateException.class);
                    return invocation.proceed();
                }
            );
            aspect.setOrder(0);
            return aspect;
        }

        @Bean
        @Role(ROLE_INFRASTRUCTURE)
        public Advisor lastAspect() {
            DefaultPointcutAdvisor aspect = new DefaultPointcutAdvisor(
                shedlockPointcut(),
                (MethodInterceptor) invocation -> {
                    aspectsCalled.add("last");
                    LockAssert.assertLocked();
                    return invocation.proceed();
                }
            );
            aspect.setOrder(200);
            return aspect;
        }

        private static AnnotationMatchingPointcut shedlockPointcut() {
            return new AnnotationMatchingPointcut(
                null,
                SchedulerLock.class,
                true
            );
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

        @SchedulerLock(name = "normal")
        public void normalMethod() {
            called.set(true);
        }
    }
}
