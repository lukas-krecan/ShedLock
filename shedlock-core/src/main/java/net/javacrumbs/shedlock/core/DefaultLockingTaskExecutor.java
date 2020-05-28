/**
 * Copyright 2009-2020 the original author or authors.
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

import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.core.LockAssert.alreadyLockedBy;

/**
 * Default {@link LockingTaskExecutor} implementation.
 */
public class DefaultLockingTaskExecutor implements LockingTaskExecutor {
    private static final Logger logger = LoggerFactory.getLogger(DefaultLockingTaskExecutor.class);
    @NonNull
    private final LockProvider lockProvider;

    public DefaultLockingTaskExecutor(@NonNull LockProvider lockProvider) {
        this.lockProvider = requireNonNull(lockProvider);
    }

    @Override
    public void executeWithLock(@NonNull Runnable task, @NonNull LockConfiguration lockConfig) {
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
    public void executeWithLock(@NonNull Task task, @NonNull LockConfiguration lockConfig) throws Throwable {
        executeWithLock(() -> {
            task.call();
            return null;
        }, lockConfig);
    }

    @Override
    @NonNull
    public <T> TaskResult<T> executeWithLock(@NonNull TaskWithResult<T> task, @NonNull LockConfiguration lockConfig) throws Throwable {
        Optional<SimpleLock> lock = lockProvider.lock(lockConfig);
        String lockName = lockConfig.getName();

        if (alreadyLockedBy(lockName)) {
            logger.debug("Already locked '{}'", lockName);
            return TaskResult.result(task.call());
        } else if (lock.isPresent()) {
            try {
                LockAssert.startLock(lockName);
                logger.debug("Locked '{}', lock will be held at most until {}", lockName, lockConfig.getLockAtMostUntil());
                return TaskResult.result(task.call());
            } finally {
                LockAssert.endLock();
                lock.get().unlock();
                if (logger.isDebugEnabled()) {
                    Instant lockAtLeastUntil = lockConfig.getLockAtLeastUntil();
                    Instant now = ClockProvider.now();
                    if (lockAtLeastUntil.isAfter(now)) {
                        logger.debug("Task finished, lock '{}' will be released at {}", lockName, lockAtLeastUntil);
                    } else {
                        logger.debug("Task finished, lock '{}' released", lockName);
                    }
                }
            }
        } else {
            logger.debug("Not executing '{}'. It's locked.", lockName);
            return TaskResult.notExecuted();
        }
    }
}
