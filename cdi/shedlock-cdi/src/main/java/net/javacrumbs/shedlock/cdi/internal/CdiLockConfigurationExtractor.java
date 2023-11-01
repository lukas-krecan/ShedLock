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
package net.javacrumbs.shedlock.cdi.internal;

import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.cdi.internal.Utils.parseDuration;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import net.javacrumbs.shedlock.cdi.SchedulerLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;

class CdiLockConfigurationExtractor {
    private final Duration defaultLockAtMostFor;
    private final Duration defaultLockAtLeastFor;

    CdiLockConfigurationExtractor(Duration defaultLockAtMostFor, Duration defaultLockAtLeastFor) {
        this.defaultLockAtMostFor = requireNonNull(defaultLockAtMostFor);
        this.defaultLockAtLeastFor = requireNonNull(defaultLockAtLeastFor);
    }

    Optional<LockConfiguration> getLockConfiguration(Method method) {
        Optional<SchedulerLock> annotation = findAnnotation(method);
        return annotation.map(this::getLockConfiguration);
    }

    private LockConfiguration getLockConfiguration(SchedulerLock annotation) {
        return new LockConfiguration(
                ClockProvider.now(), getName(annotation), getLockAtMostFor(annotation), getLockAtLeastFor(annotation));
    }

    private String getName(SchedulerLock annotation) {
        return annotation.name();
    }

    Duration getLockAtMostFor(SchedulerLock annotation) {
        return getValue(annotation.lockAtMostFor(), this.defaultLockAtMostFor, "lockAtMostFor");
    }

    Duration getLockAtLeastFor(SchedulerLock annotation) {
        return getValue(annotation.lockAtLeastFor(), this.defaultLockAtLeastFor, "lockAtLeastFor");
    }

    private Duration getValue(String stringValueFromAnnotation, Duration defaultValue, String paramName) {
        if (!stringValueFromAnnotation.isEmpty()) {
            return parseDuration(stringValueFromAnnotation);
        } else {
            return defaultValue;
        }
    }

    Optional<SchedulerLock> findAnnotation(Method method) {
        return Optional.ofNullable(method.getAnnotation(SchedulerLock.class));
    }
}
