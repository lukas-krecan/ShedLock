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
package net.javacrumbs.shedlock.spring;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockConfigurationExtractor;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

import java.lang.reflect.Method;
import java.util.Optional;

import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.MILLIS;

/**
 * Extracts configuration form Spring scheduled task. Is able to extract information from
 * <ol>
 * <li>Annotation based scheduler</li>
 * </ol>
 */
public class SpringLockConfigurationExtractor implements LockConfigurationExtractor {

    @Override
    public Optional<LockConfiguration> getLockConfiguration(Runnable task) {
        if (task instanceof ScheduledMethodRunnable) {
            Method method = ((ScheduledMethodRunnable) task).getMethod();
            SchedulerLock annotation = AnnotationUtils.findAnnotation(method, SchedulerLock.class);
            if (shouldLock(annotation)) {
                return Optional.of(new LockConfiguration(annotation.name(), now().plus(annotation.lockAtMostFor(), MILLIS)));
            }
        }
        return Optional.empty();
    }


    private boolean shouldLock(SchedulerLock annotation) {
        return annotation != null;
    }
}
