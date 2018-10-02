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

import java.time.Instant;
import java.util.Objects;

/**
 * Lock configuration.
 */
public class LockConfiguration {
    private final String name;

    /**
     * The lock is held until this instant, after that it's automatically released (the process holding it has most likely
     * died without releasing the lock) Can be ignored by providers which can detect dead processes (like Zookeeper)
     */
    private final Instant lockAtMostUntil;
    /**
     * The lock will be held until this time even if the task holding the lock finishes earlier.
     */
    private final Instant lockAtLeastUntil;

    public LockConfiguration(String name, Instant lockAtMostUntil) {
        this(name, lockAtMostUntil, Instant.now());
    }

    public LockConfiguration(String name, Instant lockAtMostUntil, Instant lockAtLeastUntil) {
        this.name = Objects.requireNonNull(name);
        this.lockAtMostUntil = Objects.requireNonNull(lockAtMostUntil);
        this.lockAtLeastUntil = Objects.requireNonNull(lockAtLeastUntil);
        if (lockAtLeastUntil.isAfter(lockAtMostUntil)) {
            throw new IllegalArgumentException("lockAtMostUntil is before lockAtLeastUntil for lock '" + name + "'.");
        }
        if (lockAtMostUntil.isBefore(Instant.now())) {
            throw new IllegalArgumentException("lockAtMostUntil is in the past for lock '" + name + "'.");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("lock name can not be empty");
        }
    }


    public String getName() {
        return name;
    }

    public Instant getLockAtMostUntil() {
        return lockAtMostUntil;
    }

    public Instant getLockAtLeastUntil() {
        return lockAtLeastUntil;
    }

    /**
     * Returns either now or lockAtLeastUntil whichever is later.
     */
    public Instant getUnlockTime() {
        Instant now = Instant.now();
        return lockAtLeastUntil.isAfter(now) ? lockAtLeastUntil : now;
    }

    @Override
    public String toString() {
        return "LockConfiguration{" +
            "name='" + name + '\'' +
            ", lockAtMostUntil=" + lockAtMostUntil +
            ", lockAtLeastUntil=" + lockAtLeastUntil +
            '}';
    }
}
