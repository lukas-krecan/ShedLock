/**
 * Copyright 2009-2017 the original author or authors.
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
package net.javacrumbs.shedlock.spring;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockConfigurationExtractor;
import net.javacrumbs.shedlock.core.ScheduledLockConfigurer;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.Optional;

import static net.javacrumbs.shedlock.core.ScheduledLockConfigurer.DEFAULT_LOCK_AT_MOST_FOR;

/**
 * Extracts configuration form Spring scheduled task. Is able to extract information from
 * <ol>
 * <li>Annotation based scheduler</li>
 * </ol>
 */
public class SpringLockConfigurationExtractor implements LockConfigurationExtractor {

    private final Logger logger = LoggerFactory.getLogger(SpringLockConfigurationExtractor.class);

    private final ScheduledLockConfigurer scheduledLockConfigurer;

    public SpringLockConfigurationExtractor() {
        this(DEFAULT_LOCK_AT_MOST_FOR);
    }

    public SpringLockConfigurationExtractor(TemporalAmount defaultLockAtMostFor) {
        this(defaultLockAtMostFor, Duration.ZERO);
    }

    public SpringLockConfigurationExtractor(TemporalAmount defaultLockAtMostFor, Duration defaultLockAtLeastFor) {
        scheduledLockConfigurer = new ScheduledLockConfigurer(defaultLockAtMostFor, defaultLockAtLeastFor);
    }

    @Override
    public Optional<LockConfiguration> getLockConfiguration(Runnable task) {
        if (task instanceof ScheduledMethodRunnable) {
            SchedulerLock annotation = findAnnotation((ScheduledMethodRunnable) task);
            if (shouldLock(annotation)) {
                return Optional.of(scheduledLockConfigurer.getLockConfiguration(annotation));
            }
        } else {
            logger.debug("Unknown task type " + task);
        }
        return Optional.empty();
    }

    SchedulerLock findAnnotation(ScheduledMethodRunnable task) {
        Method method = task.getMethod();
        SchedulerLock annotation = AnnotationUtils.findAnnotation(method, SchedulerLock.class);
        if (annotation != null) {
            return annotation;
        } else {
            // Try to find annotation on proxied class
            Class<?> targetClass = AopUtils.getTargetClass(task.getTarget());
            if (targetClass != null && !task.getTarget().getClass().equals(targetClass)) {
                try {
                    Method methodOnTarget = targetClass.getMethod(method.getName(), method.getParameterTypes());
                    return AnnotationUtils.findAnnotation(methodOnTarget, SchedulerLock.class);
                } catch (NoSuchMethodException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
    }

    private boolean shouldLock(SchedulerLock annotation) {
        return annotation != null;
    }
}
