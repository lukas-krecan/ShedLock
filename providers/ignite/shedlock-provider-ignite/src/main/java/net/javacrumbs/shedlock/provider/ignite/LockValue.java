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
package net.javacrumbs.shedlock.provider.ignite;

import java.time.LocalDateTime;
import org.apache.ignite.catalog.annotations.Column;
import org.apache.ignite.catalog.annotations.Table;

/** Value object for ShedLock cache. */
// Don't convert to record, does not work
@Table
class LockValue {
    /** Locked at time. */
    @Column("locked_at")
    private LocalDateTime lockedAt;

    /** Locked until time. */
    @Column("lock_until")
    private LocalDateTime lockUntil;

    /** Locked by hostname. */
    @Column("locked_by")
    private String lockedBy;

    @SuppressWarnings("NullAway")
    LockValue() {}
    /**
     * @param lockedAt
     *            Locked at time.
     * @param lockUntil
     *            Locked until time.
     * @param lockedBy
     *            Locked by hostname.
     */
    LockValue(LocalDateTime lockedAt, LocalDateTime lockUntil, String lockedBy) {
        this.lockedAt = lockedAt;
        this.lockUntil = lockUntil;
        this.lockedBy = lockedBy;
    }

    LockValue withLockUntil(LocalDateTime lockUntil) {
        return new LockValue(lockedAt, lockUntil, lockedBy);
    }

    /**
     * Returns locked at time.
     * @return Locked at time.
     */
    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    /**
     * Returns locked until time.
     * @return Locked until time.
     */
    public LocalDateTime getLockUntil() {
        return lockUntil;
    }

    /**
     * Returns locked by hostname.
     * @return Locked by hostname.
     */
    public String getLockedBy() {
        return lockedBy;
    }
}
