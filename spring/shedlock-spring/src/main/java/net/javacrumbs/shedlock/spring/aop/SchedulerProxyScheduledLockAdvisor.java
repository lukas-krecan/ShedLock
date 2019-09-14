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

import net.javacrumbs.shedlock.core.LockManager;
import net.javacrumbs.shedlock.core.LockableRunnable;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.NameMatchMethodPointcut;
import org.springframework.aop.support.RootClassFilter;
import org.springframework.scheduling.TaskScheduler;

class SchedulerProxyScheduledLockAdvisor extends AbstractPointcutAdvisor {
    private final Pointcut pointcut = new TaskSchedulerPointcut();
    private final Advice advice;
    private static final Logger logger = LoggerFactory.getLogger(SchedulerProxyScheduledLockAdvisor.class);

    SchedulerProxyScheduledLockAdvisor(LockManager lockManager) {
        this.advice = new LockingInterceptor(lockManager);
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
        private final LockManager lockManager;

        private LockingInterceptor(LockManager lockManager) {
            this.lockManager = lockManager;
        }


        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Object[] arguments = invocation.getArguments();
            if (arguments.length >= 1 && arguments[0] instanceof Runnable) {
                arguments[0] = new LockableRunnable((Runnable) arguments[0], lockManager);
            } else {
                logger.warn("Task scheduler first argument should be Runnable");
            }
            return invocation.proceed();
        }
    }

    private static class TaskSchedulerPointcut implements Pointcut {
        @Override
        public ClassFilter getClassFilter() {
            return new RootClassFilter(TaskScheduler.class);
        }

        @Override
        public MethodMatcher getMethodMatcher() {
            NameMatchMethodPointcut nameMatchMethodPointcut = new NameMatchMethodPointcut();
            nameMatchMethodPointcut.setMappedNames("schedule", "scheduleAtFixedRate", "scheduleWithFixedDelay");
            return nameMatchMethodPointcut;
        }
    }
}
