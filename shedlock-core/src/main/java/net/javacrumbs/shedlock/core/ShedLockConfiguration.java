/**
 * Copyright 2009-2016 the original author or authors.
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
package net.javacrumbs.shedlock.core;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;

import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.Objects.requireNonNull;

public class ShedLockConfiguration {
    private final TemporalAmount defaultLockAtMostFor;
    private final TemporalAmount defaultLockAtLeastFor;

    public ShedLockConfiguration(TemporalAmount defaultLockAtMostFor, TemporalAmount defaultLockAtLeastFor) {
        this.defaultLockAtMostFor = requireNonNull(defaultLockAtMostFor);
        this.defaultLockAtLeastFor = requireNonNull(defaultLockAtLeastFor);
    }

    private TemporalAmount getLockAtMostFor(SchedulerLock annotation) {
        long valueFromAnnotation = annotation.lockAtMostFor();
        return valueFromAnnotation >= 0 ? Duration.of(valueFromAnnotation, MILLIS) : defaultLockAtMostFor;
    }

    private TemporalAmount getLockAtLeastFor(SchedulerLock annotation) {
        long valueFromAnnotation = annotation.lockAtLeastFor();
        return valueFromAnnotation >= 0 ? Duration.of(valueFromAnnotation, MILLIS) : defaultLockAtLeastFor;
    }

    public LockConfiguration getLockConfiguration(SchedulerLock annotation) {
        Instant now = now();
        return new LockConfiguration(annotation.name(), now.plus(getLockAtMostFor(annotation)), now.plus(getLockAtLeastFor(annotation)));
    }
}
