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
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.Optional;
import java.util.concurrent.Callable;

import static java.util.Objects.requireNonNull;

public class LockingListenableFutureExecutor {
    private static final Logger logger = LoggerFactory.getLogger(LockingListenableFutureExecutor.class);
    private final LockProvider lockProvider;

    public LockingListenableFutureExecutor(LockProvider lockProvider) {
        this.lockProvider = requireNonNull(lockProvider);
    }

    public void executeWithLock(Callable<ListenableFuture<?>> task, LockConfiguration lockConfig) {
        Optional<SimpleLock> lockOpt = lockProvider.lock(lockConfig);
        String lockName = lockConfig.getName();
        if (lockOpt.isPresent()) {
            SimpleLock lock = lockOpt.get();
            try {
                logger.debug("Locked {}.", lockName);
                task.call().addCallback(r -> unlock(lock, lockName), e -> unlock(lock, lockName));
            } catch (Exception e) {
                logger.info("Error when executing task", e);
                unlock(lock, lockName);
            }
        } else {
            logger.info("Not executing {}. It's locked.", lockName);
        }
    }

    private void unlock(SimpleLock lock, String lockConfigName) {
        lock.unlock();
        logger.debug("Unlocked {}.", lockConfigName);
    }
}
