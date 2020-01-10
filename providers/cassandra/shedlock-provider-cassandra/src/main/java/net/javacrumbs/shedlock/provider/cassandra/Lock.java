package net.javacrumbs.shedlock.provider.cassandra;

import java.time.Instant;

class Lock {
    private final Instant lockUntil;
    private final Instant lockedAt;
    private final String lockedBy;

    Lock(Instant lockUntil, Instant lockedAt, String lockedBy) {
        this.lockUntil = lockUntil;
        this.lockedAt = lockedAt;
        this.lockedBy = lockedBy;
    }

    Instant getLockUntil() {
        return lockUntil;
    }

    Instant getLockedAt() {
        return lockedAt;
    }

    String getLockedBy() {
        return lockedBy;
    }
}
