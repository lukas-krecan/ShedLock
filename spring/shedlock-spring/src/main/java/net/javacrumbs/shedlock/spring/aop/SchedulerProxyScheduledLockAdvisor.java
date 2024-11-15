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

import java.lang.reflect.Field;
import net.javacrumbs.shedlock.core.DefaultLockManager;
import net.javacrumbs.shedlock.core.LockManager;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockableRunnable;
import net.javacrumbs.shedlock.spring.ExtendedLockConfigurationExtractor;
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
import org.springframework.scheduling.support.ScheduledMethodRunnable;

class SchedulerProxyScheduledLockAdvisor extends AbstractPointcutAdvisor {
    private final Pointcut pointcut = new TaskSchedulerPointcut();
    private final Advice advice;
    private static final Logger logger = LoggerFactory.getLogger(SchedulerProxyScheduledLockAdvisor.class);

    SchedulerProxyScheduledLockAdvisor(
            LockProviderSupplier lockProviderSupplier, ExtendedLockConfigurationExtractor lockConfigurationExtractor) {
        this.advice = new LockingInterceptor(lockProviderSupplier, lockConfigurationExtractor);
    }

    /** Get the Pointcut that drives this advisor. */
    @Override
    public Pointcut getPointcut() {
        return pointcut;
    }

    @Override
    public Advice getAdvice() {
        return advice;
    }

    private static class LockingInterceptor implements MethodInterceptor {
        private final LockProviderSupplier lockProviderSupplier;

        private final ExtendedLockConfigurationExtractor lockConfigurationExtractor;

        private LockingInterceptor(
                LockProviderSupplier lockProviderSupplier,
                ExtendedLockConfigurationExtractor lockConfigurationExtractor) {
            this.lockProviderSupplier = lockProviderSupplier;
            this.lockConfigurationExtractor = lockConfigurationExtractor;
        }

        @Override
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Object[] arguments = invocation.getArguments();
            if (arguments.length >= 1) {
                arguments[0] = wrapTask(arguments[0]);
            } else {
                throw new IllegalStateException("Task scheduler method does not have any arguments");
            }
            return invocation.proceed();
        }

        private Object wrapTask(Object firstArgument) throws NoSuchFieldException, IllegalAccessException {
            if (firstArgument instanceof ScheduledMethodRunnable task) {
                return wrapTask(task);
            } else if (firstArgument.getClass().getSimpleName().equals("OutcomeTrackingRunnable")) {
                // We need to access the wrapped runnable. This is the only way
                Field runnable = firstArgument.getClass().getDeclaredField("runnable");
                runnable.setAccessible(true);
                Object wrappedRunnable = runnable.get(firstArgument);
                if (wrappedRunnable instanceof ScheduledMethodRunnable task) {
                    return wrapTask(task);
                }
            }
            logger.warn("Task scheduler first argument should be ScheduledMethodRunnable or OutcomeTrackingRunnable");
            return firstArgument;
        }

        private LockableRunnable wrapTask(ScheduledMethodRunnable task) {
            LockProvider lockProvider =
                    lockProviderSupplier.supply(task.getTarget(), task.getMethod(), new Object[] {});
            LockManager lockManager = new DefaultLockManager(lockProvider, lockConfigurationExtractor);
            return new LockableRunnable(task, lockManager);
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
