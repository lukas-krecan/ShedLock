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

import java.lang.annotation.Annotation;
import java.util.Optional;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor.TaskResult;
import net.javacrumbs.shedlock.spring.ExtendedLockConfigurationExtractor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import net.javacrumbs.shedlock.support.annotation.Nullable;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;

class MethodProxyScheduledLockAdvisor extends AbstractPointcutAdvisor {
    private final Pointcut pointcut = new ComposablePointcut(methodPointcutFor(SchedulerLock.class));

    private final Advice advice;

    MethodProxyScheduledLockAdvisor(
            ExtendedLockConfigurationExtractor lockConfigurationExtractor, LockProviderSupplier lockProviderSupplier) {
        this.advice = new LockingInterceptor(lockConfigurationExtractor, lockProviderSupplier);
    }

    private static AnnotationMatchingPointcut methodPointcutFor(Class<? extends Annotation> methodAnnotationType) {
        return new AnnotationMatchingPointcut(null, methodAnnotationType, true);
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
        private final ExtendedLockConfigurationExtractor lockConfigurationExtractor;
        private final LockProviderSupplier lockProviderSupplier;

        LockingInterceptor(
                ExtendedLockConfigurationExtractor lockConfigurationExtractor,
                LockProviderSupplier lockProviderSupplier) {
            this.lockConfigurationExtractor = lockConfigurationExtractor;
            this.lockProviderSupplier = lockProviderSupplier;
        }

        @Override
        @Nullable
        public Object invoke(MethodInvocation invocation) throws Throwable {
            Class<?> returnType = invocation.getMethod().getReturnType();
            if (returnType.isPrimitive() && !void.class.equals(returnType)) {
                throw new LockingNotSupportedException("Can not lock method returning primitive value");
            }

            LockConfiguration lockConfiguration = lockConfigurationExtractor
                    .getLockConfiguration(invocation.getThis(), invocation.getMethod(), invocation.getArguments())
                    .get();

            LockProvider lockProvider = lockProviderSupplier.supply(
                    invocation.getThis(), invocation.getMethod(), invocation.getArguments());
            DefaultLockingTaskExecutor lockingTaskExecutor = new DefaultLockingTaskExecutor(lockProvider);
            TaskResult<Object> result = lockingTaskExecutor.executeWithLock(invocation::proceed, lockConfiguration);

            if (Optional.class.equals(returnType)) {
                return toOptional(result);
            } else {
                return result.getResult();
            }
        }

        @Nullable
        private static Object toOptional(TaskResult<Object> result) {
            if (result.wasExecuted()) {
                return result.getResult();
            } else {
                return Optional.empty();
            }
        }
    }
}
