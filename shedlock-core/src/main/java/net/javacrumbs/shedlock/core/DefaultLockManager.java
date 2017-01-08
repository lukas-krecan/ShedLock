/**
 * Copyright 2009-2017 the original author or authors.
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

    private final LockProvider lockProvider;
    private final LockConfigurationExtractor lockConfigurationExtractor;

    public DefaultLockManager(LockProvider lockProvider, LockConfigurationExtractor lockConfigurationExtractor) {
        this.lockProvider = requireNonNull(lockProvider);
        this.lockConfigurationExtractor = requireNonNull(lockConfigurationExtractor);
    }

    @Override
    public void executeIfNotLocked(Runnable task) {
        Optional<LockConfiguration> lockConfigOptional = lockConfigurationExtractor.getLockConfiguration(task);
        if (!lockConfigOptional.isPresent()) {
            logger.debug("No lock configuration for {}. Executing without lock.", task);
            task.run();
        } else {
            LockConfiguration lockConfig = lockConfigOptional.get();
            Optional<SimpleLock> lock = lockProvider.lock(lockConfig);
            if (lock.isPresent()) {
                try {
                    logger.debug("Locked {}.", lockConfig.getName());
                    task.run();
                } finally {
                    lock.get().unlock();
                    logger.debug("Unlocked {}.", lockConfig.getName());
                }
            } else {
                logger.info("Not executing {}. It's locked.", lockConfig.getName());
            }
        }
    }
}
