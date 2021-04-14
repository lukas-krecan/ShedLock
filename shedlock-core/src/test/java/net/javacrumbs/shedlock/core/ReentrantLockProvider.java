/**
 * Copyright 2009 the original author or authors.
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

import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lock provider based on {@link java.util.concurrent.locks.ReentrantLock}. Only one task per
 * SimpleLockProvider can be running. Useful mainly for testing.
 */
public class ReentrantLockProvider implements LockProvider {
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        if (lock.tryLock()) {
            return Optional.of(lock::unlock);
        } else {
            return Optional.empty();
        }
    }
}
