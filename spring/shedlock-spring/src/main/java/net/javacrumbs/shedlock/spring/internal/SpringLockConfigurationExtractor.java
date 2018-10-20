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
package net.javacrumbs.shedlock.spring.internal;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockConfigurationExtractor;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.Optional;

import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.Objects.requireNonNull;

public class SpringLockConfigurationExtractor implements LockConfigurationExtractor {
    private final TemporalAmount defaultLockAtMostFor;
    private final TemporalAmount defaultLockAtLeastFor;
    private final StringValueResolver embeddedValueResolver;
    private final Logger logger = LoggerFactory.getLogger(SpringLockConfigurationExtractor.class);

    public SpringLockConfigurationExtractor(TemporalAmount defaultLockAtMostFor, TemporalAmount defaultLockAtLeastFor, StringValueResolver embeddedValueResolver) {
        this.defaultLockAtMostFor = requireNonNull(defaultLockAtMostFor);
        this.defaultLockAtLeastFor = requireNonNull(defaultLockAtLeastFor);
        this.embeddedValueResolver = embeddedValueResolver;
    }


    @Override
    public Optional<LockConfiguration> getLockConfiguration(Runnable task) {
        if (task instanceof ScheduledMethodRunnable) {
            ScheduledMethodRunnable scheduledMethodRunnable = (ScheduledMethodRunnable) task;
            return getLockConfiguration(scheduledMethodRunnable.getTarget(), scheduledMethodRunnable.getMethod());
        } else {
            logger.debug("Unknown task type " + task);
        }
        return Optional.empty();
    }

    public Optional<LockConfiguration> getLockConfiguration(Object target, Method method) {
        SchedulerLock annotation = findAnnotation(target, method);
        if (shouldLock(annotation)) {
            return Optional.of(getLockConfiguration(annotation));
        } else {
            return Optional.empty();
        }
    }

    public LockConfiguration getLockConfiguration(SchedulerLock annotation) {
        Instant now = now();
        return new LockConfiguration(
            getName(annotation),
            now.plus(getLockAtMostFor(annotation)),
            now.plus(getLockAtLeastFor(annotation)));
    }

    private String getName(SchedulerLock annotation) {
        if (embeddedValueResolver != null) {
            return embeddedValueResolver.resolveStringValue(annotation.name());
        } else {
            return annotation.name();
        }
    }

    TemporalAmount getLockAtMostFor(SchedulerLock annotation) {
        return getValue(
            annotation.lockAtMostFor(),
            annotation.lockAtMostForString(),
            this.defaultLockAtMostFor,
            "lockAtMostForString"
        );
    }

    TemporalAmount getLockAtLeastFor(SchedulerLock annotation) {
        return getValue(
            annotation.lockAtLeastFor(),
            annotation.lockAtLeastForString(),
            this.defaultLockAtLeastFor,
            "lockAtLeastForString"
        );
    }

    private TemporalAmount getValue(long valueFromAnnotation, String stringValueFromAnnotation, TemporalAmount defaultValue, final String paramName) {
        if (valueFromAnnotation >= 0) {
            return Duration.of(valueFromAnnotation, MILLIS);
        } else if (StringUtils.hasText(stringValueFromAnnotation)) {
            if (embeddedValueResolver != null) {
                stringValueFromAnnotation = embeddedValueResolver.resolveStringValue(stringValueFromAnnotation);
            }
            try {
                return Duration.of(Long.valueOf(stringValueFromAnnotation), MILLIS);
            } catch (NumberFormatException nfe) {
                try {
                    return Duration.parse(stringValueFromAnnotation);
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Invalid " + paramName + " value \"" + stringValueFromAnnotation + "\" - cannot parse into long nor duration");
                }
            }
        } else {
            return defaultValue;
        }
    }

    SchedulerLock findAnnotation(Object target, Method method) {
        SchedulerLock annotation = findAnnotation(method);
        if (annotation != null) {
            return annotation;
        } else {
            // Try to find annotation on proxied class
            Class<?> targetClass = AopUtils.getTargetClass(target);
            if (targetClass != null && !target.getClass().equals(targetClass)) {
                try {
                    Method methodOnTarget = targetClass
                        .getMethod(method.getName(), method.getParameterTypes());
                    return findAnnotation(methodOnTarget);
                } catch (NoSuchMethodException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
    }

    SchedulerLock findAnnotation(Method method) {
        return AnnotatedElementUtils.getMergedAnnotation(method, SchedulerLock.class);
    }

    private boolean shouldLock(SchedulerLock annotation) {
        return annotation != null;
    }
}
