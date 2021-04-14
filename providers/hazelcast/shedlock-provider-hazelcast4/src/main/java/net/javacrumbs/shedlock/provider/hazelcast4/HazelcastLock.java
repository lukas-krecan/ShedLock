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
package net.javacrumbs.shedlock.provider.hazelcast4;

import net.javacrumbs.shedlock.core.LockConfiguration;

import java.io.Serializable;
import java.time.Instant;

/**
 * Hazelcast lock entity.
 * <p>
 * It's used to persist lock information into Hazelcast instances (cluster).
 */
class HazelcastLock implements Serializable {

    private final String name;

    private final Instant lockAtMostUntil;

    private final Instant lockAtLeastUntil;

    /**
     * Moment when the lock is expired, so unlockable.
     * The first value of this is {@link #lockAtMostUntil}.
     */
    private final Instant timeToLive;

    private HazelcastLock(final String name, final Instant lockAtMostUntil, final Instant lockAtLeastUntil, final Instant timeToLive) {
        this.name = name;
        this.lockAtMostUntil = lockAtMostUntil;
        this.lockAtLeastUntil = lockAtLeastUntil;
        this.timeToLive = timeToLive;
    }

    boolean isExpired(Instant now) {
        return !now.isBefore(getTimeToLive());
    }

    /**
     * Instantiate {@link HazelcastLock} with {@link LockConfiguration} and Hazelcast member UUID.
     *
     * @param configuration
     * @return the new instance of {@link HazelcastLock}.
     */
    static HazelcastLock fromConfigurationWhereTtlIsUntilTime(final LockConfiguration configuration) {
        return new HazelcastLock(configuration.getName(), configuration.getLockAtMostUntil(), configuration.getLockAtLeastUntil(), configuration.getLockAtMostUntil());
    }

    /**
     * Copy an existing {@link HazelcastLock} and change its time to live.
     *
     * @param lock
     * @return the new instance of {@link HazelcastLock}.
     */
    static HazelcastLock fromLockWhereTtlIsReduceToLeastTime(final HazelcastLock lock) {
        return new HazelcastLock(lock.name, lock.lockAtMostUntil, lock.lockAtLeastUntil, lock.lockAtLeastUntil);
    }

    String getName() {
        return name;
    }

    Instant getLockAtLeastUntil() {
        return lockAtLeastUntil;
    }

    Instant getTimeToLive() {
        return timeToLive;
    }

    @Override
    public String toString() {
        return "HazelcastLock{" +
                "name='" + name + '\'' +
                ", lockAtMostUntil=" + lockAtMostUntil +
                ", lockAtLeastUntil=" + lockAtLeastUntil +
                ", timeToLive=" + timeToLive +
                '}';
    }

}
