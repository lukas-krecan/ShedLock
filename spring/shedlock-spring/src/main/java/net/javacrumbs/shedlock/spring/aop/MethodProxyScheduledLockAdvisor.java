/**
 * Copyright 2009-2017 the original author or authors.
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
package net.javacrumbs.shedlock.spring.aop;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.core.SchedulerLock;
import net.javacrumbs.shedlock.spring.internal.SpringLockConfigurationExtractor;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;

class MethodProxyScheduledLockAdvisor extends AbstractPointcutAdvisor {
    private final AnnotationMatchingPointcut pointcut = AnnotationMatchingPointcut.forMethodAnnotation(SchedulerLock.class);
    private final Advice advice;

    MethodProxyScheduledLockAdvisor(SpringLockConfigurationExtractor lockConfigurationExtractor, LockingTaskExecutor lockingTaskExecutor) {
        this.advice = new LockingInterceptor(lockConfigurationExtractor, lockingTaskExecutor);
    }

    /**
     * Get the Pointcut that drives this advisor.
     */
    @Override
    public Pointcut getPointcut() {
        return pointcut;
    }

    @Override
    public Advice getAdvice() {
        return advice;
    }

    private static class LockingInterceptor implements MethodInterceptor {
        private final SpringLockConfigurationExtractor lockConfigurationExtractor;
        private final LockingTaskExecutor lockingTaskExecutor;

        LockingInterceptor(SpringLockConfigurationExtractor lockConfigurationExtractor, LockingTaskExecutor lockingTaskExecutor) {
            this.lockConfigurationExtractor = lockConfigurationExtractor;
            this.lockingTaskExecutor = lockingTaskExecutor;
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Class<?> returnType = invocation.getMethod().getReturnType();
            if (!void.class.equals(returnType) && !Void.class.equals(returnType)) {
                throw new LockingNotSupportedException();
            }

            LockConfiguration lockConfiguration = lockConfigurationExtractor.getLockConfiguration(invocation.getThis(), invocation.getMethod()).get();
            lockingTaskExecutor.executeWithLock((LockingTaskExecutor.Task) invocation::proceed, lockConfiguration);
            return null;
        }
    }
}
