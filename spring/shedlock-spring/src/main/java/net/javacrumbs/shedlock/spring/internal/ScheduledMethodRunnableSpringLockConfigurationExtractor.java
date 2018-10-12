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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.util.StringValueResolver;

import java.time.temporal.TemporalAmount;
import java.util.Optional;

/**
 * Extracts configuration form Spring scheduled task. Is able to extract information from
 * <ol>
 * <li>Annotation based scheduler</li>
 * </ol>
 */
public class ScheduledMethodRunnableSpringLockConfigurationExtractor extends SpringLockConfigurationExtractor implements LockConfigurationExtractor {
    private final Logger logger = LoggerFactory.getLogger(ScheduledMethodRunnableSpringLockConfigurationExtractor.class);

    public ScheduledMethodRunnableSpringLockConfigurationExtractor(
        TemporalAmount defaultLockAtMostFor,
        TemporalAmount defaultLockAtLeastFor,
        StringValueResolver embeddedValueResolver
    ) {
        super(defaultLockAtMostFor, defaultLockAtLeastFor, embeddedValueResolver);
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
}
