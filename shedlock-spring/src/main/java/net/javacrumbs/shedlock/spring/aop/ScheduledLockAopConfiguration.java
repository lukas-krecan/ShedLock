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
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.time.Duration;

@Aspect
public class ScheduledLockAopConfiguration {
    private final SpringLockConfigurationExtractor lockConfigurationExtractor;
    private final LockingTaskExecutor lockingTaskExecutor;

    public ScheduledLockAopConfiguration(LockingTaskExecutor lockingTaskExecutor) {
        this.lockingTaskExecutor = lockingTaskExecutor;

        //FIXME:
        lockConfigurationExtractor = new SpringLockConfigurationExtractor(SpringLockConfigurationExtractor.DEFAULT_LOCK_AT_MOST_FOR, Duration.ZERO, null);
    }

    /**
     * Supports only void method since we do not know what to return if the task is locked.
     */
    @Around("@annotation(schedulerLockAnnotation)")
    void lockIt(ProceedingJoinPoint pjp, SchedulerLock schedulerLockAnnotation) throws Throwable {
        if (pjp.getSignature() instanceof MethodSignature) {
            Class<?> returnType = ((MethodSignature) pjp.getSignature()).getMethod().getReturnType();
            if (!void.class.equals(returnType) && !Void.class.equals(returnType)) {
                throw new LockingNotSupportedException();
            }
        }

        LockConfiguration lockConfiguration = lockConfigurationExtractor.getLockConfiguration(schedulerLockAnnotation);
        lockingTaskExecutor.executeWithLock((LockingTaskExecutor.Task) pjp::proceed, lockConfiguration);
    }
}
