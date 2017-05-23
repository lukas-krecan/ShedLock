/**
 * Copyright 2009-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package net.javacrumbs.shedlock.spring;

import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.Objects.requireNonNull;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.util.StringValueResolver;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockConfigurationExtractor;
import net.javacrumbs.shedlock.core.SchedulerLock;

/**
 * Extracts configuration form Spring scheduled task. Is able to extract information from <ol> <li>Annotation based
 * scheduler</li> </ol>
 */
public class SpringLockConfigurationExtractor
        implements
        LockConfigurationExtractor,
        EmbeddedValueResolverAware {
    static final Duration DEFAULT_LOCK_AT_MOST_FOR = Duration.of(1, ChronoUnit.HOURS);
    private final Logger logger = LoggerFactory.getLogger(SpringLockConfigurationExtractor.class);

    private final TemporalAmount defaultLockAtMostFor;
    private final Duration defaultLockAtLeastFor;

    private StringValueResolver embeddedValueResolver;

    public SpringLockConfigurationExtractor() {
        this(DEFAULT_LOCK_AT_MOST_FOR);
    }

    public SpringLockConfigurationExtractor(TemporalAmount defaultLockAtMostFor) {
        this(defaultLockAtMostFor, Duration.ZERO);
    }

    public SpringLockConfigurationExtractor(
            TemporalAmount defaultLockAtMostFor,
            Duration defaultLockAtLeastFor) {
        this.defaultLockAtMostFor = requireNonNull(defaultLockAtMostFor);
        this.defaultLockAtLeastFor = requireNonNull(defaultLockAtLeastFor);
    }

    @Override
    public void setEmbeddedValueResolver(StringValueResolver resolver) {
        this.embeddedValueResolver = resolver;
    }

    @Override
    public Optional<LockConfiguration> getLockConfiguration(Runnable task) {
        if (task instanceof ScheduledMethodRunnable) {
            SchedulerLock annotation = findAnnotation((ScheduledMethodRunnable) task);
            if (shouldLock(annotation)) {
                Instant now = now();
                return Optional.of(
                    new LockConfiguration(
                        annotation.name(),
                        now.plus(getLockAtMostFor(annotation)),
                        now.plus(getLockAtLeastFor(annotation))));
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
                    Method methodOnTarget = targetClass
                        .getMethod(method.getName(), method.getParameterTypes());
                    return AnnotationUtils.findAnnotation(methodOnTarget, SchedulerLock.class);
                } catch (NoSuchMethodException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
    }

    TemporalAmount getLockAtMostFor(SchedulerLock annotation) {
        long valueFromAnnotation = annotation.lockAtMostFor();
        if (valueFromAnnotation < 0) {
            String lockAtMostForString = annotation.lockAtMostForString();
            if (lockAtMostForString.length() > 0) {
                if (this.embeddedValueResolver != null) {
                    lockAtMostForString = this.embeddedValueResolver
                        .resolveStringValue(lockAtMostForString);
                }
                if (lockAtMostForString != null && lockAtMostForString.length() > 0) {
                    try {
                        valueFromAnnotation = Long.valueOf(lockAtMostForString);
                    } catch (NumberFormatException nfe) {
                        logger.error("Unparseable lockAtMostFor", nfe);
                    }
                }
            }
        }
        return valueFromAnnotation >= 0
                ? Duration.of(valueFromAnnotation, MILLIS)
                : defaultLockAtMostFor;
    }

    Duration getLockAtLeastFor(SchedulerLock annotation) {
        long valueFromAnnotation = annotation.lockAtLeastFor();
        if (valueFromAnnotation < 0) {
            String lockAtLeastForString = annotation.lockAtLeastForString();
            if (this.embeddedValueResolver != null) {
                lockAtLeastForString = this.embeddedValueResolver
                    .resolveStringValue(lockAtLeastForString);
            }
            if (lockAtLeastForString != null && lockAtLeastForString.length() > 0) {
                try {
                    valueFromAnnotation = Long.valueOf(lockAtLeastForString);
                } catch (NumberFormatException nfe) {
                    logger.error("Unparseable lockAtLeastFor", nfe);
                }
            }
        }
        return valueFromAnnotation >= 0
                ? Duration.of(valueFromAnnotation, MILLIS)
                : defaultLockAtLeastFor;
    }

    private boolean shouldLock(SchedulerLock annotation) {
        return annotation != null;
    }
}
