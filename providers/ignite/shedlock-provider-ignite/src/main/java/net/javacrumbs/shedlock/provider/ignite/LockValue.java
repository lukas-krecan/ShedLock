package net.javacrumbs.shedlock.provider.ignite;

import java.time.Instant;

/**
 * Value object for ShedLock cache.
 */
public class LockValue {
    /** Locked at time. */
    private Instant lockedAt;

    /** Locked until time. */
    private Instant lockUntil;

    /** Locked by hostname. */
    private String lockedBy;

    /**
     * Default constructor.
     */
    public LockValue() {
    }

    /**
     * Copy constructor.
     */
    public LockValue(LockValue copy) {
        this.lockedAt = copy.lockedAt;
        this.lockUntil = copy.lockUntil;
        this.lockedBy = copy.lockedBy;
    }

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

    /**
     * @return Locked at time.
     */
    public Instant getLockedAt() {
        return lockedAt;
    }

    /**
     * @param lockedAt Locked at time.
     */
    public void setLockedAt(Instant lockedAt) {
        this.lockedAt = lockedAt;
    }

    /**
     * @return Locked until time.
     */
    public Instant getLockUntil() {
        return lockUntil;
    }

    /**
     * @param lockUntil Locked until time.
     */
    public void setLockUntil(Instant lockUntil) {
        this.lockUntil = lockUntil;
    }

    /**
     * @return Locked by hostname.
     */
    public String getLockedBy() {
        return lockedBy;
    }

    /**
     * @param lockedBy Locked by hostname.
     */
    public void setLockedBy(String lockedBy) {
        this.lockedBy = lockedBy;
    }
}
