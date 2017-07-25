package net.javacrumbs.shedlock.provider.hazelcast;

import net.javacrumbs.shedlock.core.LockConfiguration;

import java.io.Serializable;
import java.time.Instant;

/**
 * Lock representation.
 */
class HazelcastLock implements Serializable {

    private final String name;

    private final Instant lockAtMostUntil;

    private final Instant lockAtLeastUntil;

    private final String clusterMemberUuid;

    private Instant unlockTime;

    public HazelcastLock(String name, Instant lockAtMostUntil, Instant lockAtLeastUntil, String clusterMemberUuid) {
        this.name = name;
        this.lockAtMostUntil = lockAtMostUntil;
        this.lockAtLeastUntil = lockAtLeastUntil;
        this.unlockTime = lockAtMostUntil;
        this.clusterMemberUuid = clusterMemberUuid;
    }

    public static HazelcastLock fromLockConfiguration(final LockConfiguration configuration, final String clusterMemberUuid) {
        return new HazelcastLock(configuration.getName(), configuration.getLockAtMostUntil(), configuration.getLockAtLeastUntil(), clusterMemberUuid);
    }

    public String getName() {
        return name;
    }

    public String getClusterMemberUuid() {
        return clusterMemberUuid;
    }

    public Instant getLockAtMostUntil() {
        return lockAtMostUntil;
    }

    public Instant getLockAtLeastUntil() {
        return lockAtLeastUntil;
    }

    public Instant getUnlockTime() {
        return unlockTime;
    }

    public void setUnlockTime(final Instant unlockTime) {
        this.unlockTime = unlockTime;
    }

    @Override
    public String toString() {
        return "HazelcastLock{" +
                "name='" + name + '\'' +
                ", lockAtMostUntil=" + lockAtMostUntil +
                ", lockAtLeastUntil=" + lockAtLeastUntil +
                ", clusterMemberUuid='" + clusterMemberUuid + '\'' +
                ", unlockTime=" + unlockTime +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HazelcastLock that = (HazelcastLock) o;
        return getName() != null ? getName().equals(that.getName()) : that.getName() == null;
    }

    @Override
    public int hashCode() {
        return getName() != null ? getName().hashCode() : 0;
    }
}
