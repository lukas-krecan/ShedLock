package net.javacrumbs.shedlock.provider.elasticsearch.model;

public class LockPOJO {

    private final String name;
    private final String lockedBy;
    private final long lockedAt;
    private final long lockUntil;

    public LockPOJO(String name, String lockedBy, long lockedAt, long lockUntil) {
        this.name = name;
        this.lockedBy = lockedBy;
        this.lockedAt = lockedAt;
        this.lockUntil = lockUntil;
    }

    public String getName() {
        return name;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public long getLockedAt() {
        return lockedAt;
    }

    public long getLockUntil() {
        return lockUntil;
    }
}
