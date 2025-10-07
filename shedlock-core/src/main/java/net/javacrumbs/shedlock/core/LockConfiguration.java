/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.core;

import static net.javacrumbs.shedlock.core.ClockProvider.now;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Lock configuration. */
public class LockConfiguration {
    private final Instant createdAt;

    private final String name;

    /**
     * The lock is held until this duration passes, after that it's automatically
     * released (the process holding it has most likely died without releasing the
     * lock) Can be ignored by providers which can detect dead processes (like
     * Zookeeper)
     */
    private final Duration lockAtMostFor;

    /**
     * The lock will be held at least this duration even if the task holding the
     * lock finishes earlier.
     */
    private final Duration lockAtLeastFor;

    /**
     * Creates LockConfiguration. There are two types of lock providers. One that
     * uses "db time" which requires relative values of lockAtMostFor and
     * lockAtLeastFor (currently it's only JdbcTemplateLockProvider). Second type of
     * lock provider uses absolute time calculated from `createdAt`.
     *
     * @param createdAt the instant the lock was created
     * @param name the name of the lock
     * @param lockAtMostFor the maximum duration to hold the lock
     * @param lockAtLeastFor the minimum duration to hold the lock
     */
    public LockConfiguration(Instant createdAt, String name, Duration lockAtMostFor, Duration lockAtLeastFor) {
        this.createdAt = Objects.requireNonNull(createdAt);
        this.name = Objects.requireNonNull(name);
        this.lockAtMostFor = Objects.requireNonNull(lockAtMostFor);
        this.lockAtLeastFor = Objects.requireNonNull(lockAtLeastFor);
        if (lockAtLeastFor.compareTo(lockAtMostFor) > 0) {
            throw new IllegalArgumentException("lockAtLeastFor is longer than lockAtMostFor for lock '" + name + "'.");
        }
        if (lockAtMostFor.isNegative()) {
            throw new IllegalArgumentException("lockAtMostFor is negative '" + name + "'.");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("lock name can not be empty");
        }
    }

    public String getName() {
        return name;
    }

    public Instant getLockAtMostUntil() {
        return createdAt.plus(lockAtMostFor);
    }

    public Instant getLockAtLeastUntil() {
        return createdAt.plus(lockAtLeastFor);
    }

    /** Returns either now or lockAtLeastUntil whichever is later. */
    public Instant getUnlockTime() {
        Instant now = now();
        Instant lockAtLeastUntil = getLockAtLeastUntil();
        return lockAtLeastUntil.isAfter(now) ? lockAtLeastUntil : now;
    }

    public Duration getLockAtLeastFor() {
        return lockAtLeastFor;
    }

    public Duration getLockAtMostFor() {
        return lockAtMostFor;
    }

    @Override
    public String toString() {
        return "LockConfiguration{" + "name='" + name + '\'' + ", lockAtMostFor=" + lockAtMostFor + ", lockAtLeastFor="
                + lockAtLeastFor + '}';
    }
}
