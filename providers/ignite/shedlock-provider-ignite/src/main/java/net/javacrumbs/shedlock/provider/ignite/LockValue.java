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
package net.javacrumbs.shedlock.provider.ignite;

import java.time.Instant;

/**
 * Value object for ShedLock cache.
 */
class LockValue {
    /** Locked at time. */
    private final Instant lockedAt;

    /** Locked until time. */
    private final Instant lockUntil;

    /** Locked by hostname. */
    private final String lockedBy;

    /**
     * @param lockedAt Locked at time.
     * @param lockUntil Locked until time.
     * @param lockedBy Locked by hostname.
     */
    public LockValue(Instant lockedAt, Instant lockUntil, String lockedBy) {
        this.lockedAt = lockedAt;
        this.lockUntil = lockUntil;
        this.lockedBy = lockedBy;
    }

    LockValue withLockUntil(Instant lockUntil) {
        return new LockValue(lockedAt, lockUntil, lockedBy);
    }

    /**
     * @return Locked at time.
     */
    public Instant getLockedAt() {
        return lockedAt;
    }

    /**
     * @return Locked until time.
     */
    public Instant getLockUntil() {
        return lockUntil;
    }

    /**
     * @return Locked by hostname.
     */
    public String getLockedBy() {
        return lockedBy;
    }
}
