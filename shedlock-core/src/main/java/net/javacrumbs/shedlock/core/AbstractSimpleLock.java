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

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public abstract class AbstractSimpleLock implements SimpleLock {
    private boolean valid = true;
    protected final LockConfiguration lockConfiguration;

    protected AbstractSimpleLock(LockConfiguration lockConfiguration) {
        this.lockConfiguration = lockConfiguration;
    }

    @Override
    public final void unlock() {
        checkValidity();
        doUnlock();
        valid = false;
    }

    protected abstract void doUnlock();

    @Override
    @NonNull
    @Deprecated
    public final Optional<SimpleLock> extend(@NonNull Instant lockAtMostUntil, @NonNull Instant lockAtLeastUntil) {
        Instant now = Instant.now();
        return extend(Duration.between(now, lockAtMostUntil), Duration.between(now, lockAtLeastUntil));
    }

    @Override
    public @NonNull Optional<SimpleLock> extend(@NonNull Duration lockAtMostFor, @NonNull Duration lockAtLeastFor) {
        checkValidity();
        Optional<SimpleLock> result = doExtend(new LockConfiguration(ClockProvider.now(), lockConfiguration.getName(), lockAtMostFor, lockAtLeastFor));
        valid = false;
        return result;
    }

    protected Optional<SimpleLock> doExtend(LockConfiguration newConfiguration) {
        throw new UnsupportedOperationException();
    }

    private void checkValidity() {
        if (!valid) {
            throw new IllegalStateException("Lock " + lockConfiguration.getName() + " is not valid, it has already been unlocked or extended");
        }
    }
}
