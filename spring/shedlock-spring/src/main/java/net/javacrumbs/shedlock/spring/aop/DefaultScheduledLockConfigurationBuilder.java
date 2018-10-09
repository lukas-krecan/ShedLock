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
package net.javacrumbs.shedlock.spring.aop;

import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.aop.ScheduledLockConfigurationBuilder.ConfiguredScheduledLockConfigurationBuilder;
import net.javacrumbs.shedlock.spring.aop.ScheduledLockConfigurationBuilder.ScheduledLockConfigurationBuilderWithoutDefaultLockAtMostFor;
import net.javacrumbs.shedlock.spring.internal.SpringLockConfigurationExtractor;

import java.time.Duration;
import java.time.temporal.TemporalAmount;

class DefaultScheduledLockConfigurationBuilder
    implements ScheduledLockConfigurationBuilder, ConfiguredScheduledLockConfigurationBuilder, ScheduledLockConfigurationBuilderWithoutDefaultLockAtMostFor {
    private final LockProvider lockProvider;

    private TemporalAmount defaultLockAtMostFor = SpringLockConfigurationExtractor.DEFAULT_LOCK_AT_MOST_FOR;

    private TemporalAmount defaultLockAtLeastFor = Duration.ZERO;

    DefaultScheduledLockConfigurationBuilder(LockProvider lockProvider) {
        this.lockProvider = lockProvider;
    }

    @Override
    public ConfiguredScheduledLockConfigurationBuilder withDefaultLockAtMostFor(TemporalAmount defaultLockAtMostFor) {
        this.defaultLockAtMostFor = defaultLockAtMostFor;
        return this;
    }

    @Override
    public ConfiguredScheduledLockConfigurationBuilder withDefaultLockAtLeastFor(TemporalAmount defaultLockAtLeastFor) {
        this.defaultLockAtLeastFor = defaultLockAtLeastFor;
        return this;
    }

    @Override
    public ScheduledLockConfiguration build() {
        return new ScheduledLockAopConfiguration(new DefaultLockingTaskExecutor(lockProvider), defaultLockAtMostFor, defaultLockAtLeastFor);
    }
}
