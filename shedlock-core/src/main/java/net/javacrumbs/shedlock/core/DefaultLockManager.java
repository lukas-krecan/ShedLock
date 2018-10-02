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
package net.javacrumbs.shedlock.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static java.util.Objects.requireNonNull;


/**
 * Default implementation {@link LockManager} implementation.
 */
public class DefaultLockManager implements LockManager {
    private static final Logger logger = LoggerFactory.getLogger(DefaultLockManager.class);

    private final LockingTaskExecutor lockingTaskExecutor;
    private final LockConfigurationExtractor lockConfigurationExtractor;

    public DefaultLockManager(LockProvider lockProvider, LockConfigurationExtractor lockConfigurationExtractor) {
        this(new DefaultLockingTaskExecutor(lockProvider), lockConfigurationExtractor);
    }

    public DefaultLockManager(LockingTaskExecutor lockingTaskExecutor, LockConfigurationExtractor lockConfigurationExtractor) {
        this.lockingTaskExecutor = requireNonNull(lockingTaskExecutor);
        this.lockConfigurationExtractor = requireNonNull(lockConfigurationExtractor);
    }

    @Override
    public void executeWithLock(Runnable task) {
        Optional<LockConfiguration> lockConfigOptional = lockConfigurationExtractor.getLockConfiguration(task);
        if (!lockConfigOptional.isPresent()) {
            logger.debug("No lock configuration for {}. Executing without lock.", task);
            task.run();
        } else {
            lockingTaskExecutor.executeWithLock(task, lockConfigOptional.get());
        }
    }
}
