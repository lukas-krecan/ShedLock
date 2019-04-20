/**
 * Copyright 2009-2019 the original author or authors.
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
package micronaut.test;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.jetbrains.annotations.NotNull;

import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lock provider based on {@link ReentrantLock}. Only one task per
 * SimpleLockProvider can be running. Useful mainly for testing.
 */
@Singleton
public class ReentrantLockProvider implements LockProvider {
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    @NotNull
    public Optional<SimpleLock> lock(@NotNull LockConfiguration lockConfiguration) {
        if (lock.tryLock()) {
            return Optional.of(lock::unlock);
        } else {
            return Optional.empty();
        }
    }
}
