/**
 * Copyright 2009-2019 the original author or authors.
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
package net.javacrumbs.shedlock.micronaut.internal;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ExecutableMethod;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.micronaut.SchedulerLock;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAmount;
import java.util.Optional;

import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.Objects.requireNonNull;

class MicronautLockConfigurationExtractor {
    private final TemporalAmount defaultLockAtMostFor;
    private final TemporalAmount defaultLockAtLeastFor;
    private final Logger logger = LoggerFactory.getLogger(MicronautLockConfigurationExtractor.class);

    MicronautLockConfigurationExtractor(@NotNull TemporalAmount defaultLockAtMostFor, @NotNull TemporalAmount defaultLockAtLeastFor) {
        this.defaultLockAtMostFor = requireNonNull(defaultLockAtMostFor);
        this.defaultLockAtLeastFor = requireNonNull(defaultLockAtLeastFor);
    }


    @NotNull
    Optional<LockConfiguration> getLockConfiguration(@NotNull ExecutableMethod<Object, Object> method) {
        AnnotationData annotation = findAnnotation(method);
        if (shouldLock(annotation)) {
            return Optional.of(getLockConfiguration(annotation));
        } else {
            return Optional.empty();
        }
    }

    private LockConfiguration getLockConfiguration(AnnotationData annotation) {
        Instant now = now();
        return new LockConfiguration(
            getName(annotation),
            now.plus(getLockAtMostFor(annotation)),
            now.plus(getLockAtLeastFor(annotation)));
    }

    private String getName(AnnotationData annotation) {
        return annotation.getName();
    }

    TemporalAmount getLockAtMostFor(AnnotationData annotation) {
        return getValue(
            annotation.getLockAtMostFor(),
            annotation.getLockAtMostForString(),
            this.defaultLockAtMostFor,
            "lockAtMostForString"
        );
    }

    TemporalAmount getLockAtLeastFor(AnnotationData annotation) {
        return getValue(
            annotation.getLockAtLeastFor(),
            annotation.getLockAtLeastForString(),
            this.defaultLockAtLeastFor,
            "lockAtLeastForString"
        );
    }

    private TemporalAmount getValue(long valueFromAnnotation, String stringValueFromAnnotation, TemporalAmount defaultValue, final String paramName) {
        if (valueFromAnnotation >= 0) {
            return Duration.of(valueFromAnnotation, MILLIS);
        } else if (StringUtils.hasText(stringValueFromAnnotation)) {
            try {
                return Duration.of(Long.parseLong(stringValueFromAnnotation), MILLIS);
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

    AnnotationData findAnnotation(ExecutableMethod<Object, Object> method) {
        Optional<AnnotationValue<SchedulerLock>> annotationValue = method.findAnnotation(SchedulerLock.class);
        return annotationValue.map(schedulerLockAnnotationValue -> new AnnotationData(
            schedulerLockAnnotationValue.get("name", String.class).orElseThrow(() -> new IllegalStateException("name param not specified")),
            schedulerLockAnnotationValue.get("lockAtMostFor", Long.class).orElseThrow(() -> new IllegalStateException("lockAtMostFor param not specified")),
            schedulerLockAnnotationValue.get("lockAtMostForString", String.class).orElseThrow(() -> new IllegalStateException("lockAtMostForString param not specified")),
            schedulerLockAnnotationValue.get("lockAtLeastFor", Long.class).orElseThrow(() -> new IllegalStateException("lockAtLeastFor param not specified")),
            schedulerLockAnnotationValue.get("lockAtLeastForString", String.class).orElseThrow(() -> new IllegalStateException("lockAtLeastForString param not specified"))
        )).orElse(null);
    }

    private boolean shouldLock(AnnotationData annotation) {
        return annotation != null;
    }

    static class AnnotationData {
        private final String name;
        private final long lockAtMostFor;
        private final String lockAtMostForString;
        private final long lockAtLeastFor;
        private final String lockAtLeastForString;

        private AnnotationData(String name, long lockAtMostFor, String lockAtMostForString, long lockAtLeastFor, String lockAtLeastForString) {
            this.name = name;
            this.lockAtMostFor = lockAtMostFor;
            this.lockAtMostForString = lockAtMostForString;
            this.lockAtLeastFor = lockAtLeastFor;
            this.lockAtLeastForString = lockAtLeastForString;
        }

        public String getName() {
            return name;
        }

        public long getLockAtMostFor() {
            return lockAtMostFor;
        }

        public String getLockAtMostForString() {
            return lockAtMostForString;
        }

        public long getLockAtLeastFor() {
            return lockAtLeastFor;
        }

        public String getLockAtLeastForString() {
            return lockAtLeastForString;
        }
    }
}


