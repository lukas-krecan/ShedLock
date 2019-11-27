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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ExecutableMethod;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.micronaut.SchedulerLock;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Optional;

import static java.time.Instant.now;
import static java.util.Objects.requireNonNull;

class MicronautLockConfigurationExtractor {
    private final TemporalAmount defaultLockAtMostFor;
    private final TemporalAmount defaultLockAtLeastFor;
    private final ConversionService<?> conversionService;

    MicronautLockConfigurationExtractor(@NotNull TemporalAmount defaultLockAtMostFor, @NotNull TemporalAmount defaultLockAtLeastFor, @NotNull ConversionService<?> conversionService) {
        this.defaultLockAtMostFor = requireNonNull(defaultLockAtMostFor);
        this.defaultLockAtLeastFor = requireNonNull(defaultLockAtLeastFor);
        this.conversionService = conversionService;
    }


    @NotNull
    Optional<LockConfiguration> getLockConfiguration(@NotNull ExecutableMethod<Object, Object> method) {
        Optional<AnnotationValue<SchedulerLock>> annotation = findAnnotation(method);
        return annotation.map(this::getLockConfiguration);
    }

    private LockConfiguration getLockConfiguration(AnnotationValue<SchedulerLock> annotation) {
        Instant now = now();
        return new LockConfiguration(
            getName(annotation),
            now.plus(getLockAtMostFor(annotation)),
            now.plus(getLockAtLeastFor(annotation)));
    }

    private String getName(AnnotationValue<SchedulerLock> annotation) {
        return annotation.getRequiredValue("name", String.class);
    }

    TemporalAmount getLockAtMostFor(AnnotationValue<SchedulerLock> annotation) {
        return getValue(
            annotation,
            this.defaultLockAtMostFor,
            "lockAtMostFor"
        );
    }

    TemporalAmount getLockAtLeastFor(AnnotationValue<SchedulerLock> annotation) {
        return getValue(
            annotation,
            this.defaultLockAtLeastFor,
            "lockAtLeastFor"
        );
    }

    private TemporalAmount getValue(AnnotationValue<SchedulerLock> annotation, TemporalAmount defaultValue, String paramName) {
        String stringValueFromAnnotation = annotation.get(paramName, String.class).orElse("");
        if (StringUtils.hasText(stringValueFromAnnotation)) {
            return conversionService.convert(stringValueFromAnnotation, Duration.class)
                .orElseThrow(() -> new IllegalArgumentException("Invalid " + paramName + " value \"" + stringValueFromAnnotation + "\" - cannot parse into duration"));
        } else {
            return defaultValue;
        }
    }

    Optional<AnnotationValue<SchedulerLock>> findAnnotation(ExecutableMethod<Object, Object> method) {
        return method.findAnnotation(SchedulerLock.class);
    }
}


