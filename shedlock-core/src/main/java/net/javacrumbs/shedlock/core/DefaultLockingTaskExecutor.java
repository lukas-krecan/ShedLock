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
 * Default {@link LockingTaskExecutor} implementation.
 */
public class DefaultLockingTaskExecutor implements LockingTaskExecutor {
    private static final Logger logger = LoggerFactory.getLogger(DefaultLockingTaskExecutor.class);
    private final LockProvider lockProvider;

    public DefaultLockingTaskExecutor(LockProvider lockProvider) {
        this.lockProvider = requireNonNull(lockProvider);
    }

    @Override
    public void executeWithLock(Runnable task, LockConfiguration lockConfig) {
        try {
            executeWithLock((Task) task::run, lockConfig);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable throwable) {
            // Should not happen
            throw new IllegalStateException(throwable);
        }
    }

    @Override
    public void executeWithLock(Task task, LockConfiguration lockConfig) throws Throwable{
        Optional<SimpleLock> lock = lockProvider.lock(lockConfig);
        if (lock.isPresent()) {
            try {
                logger.debug("Locked {}.", lockConfig.getName());
                task.call();
            } finally {
                lock.get().unlock();
                logger.debug("Unlocked {}.", lockConfig.getName());
            }
        } else {
            logger.debug("Not executing {}. It's locked.", lockConfig.getName());
        }
    }
}
