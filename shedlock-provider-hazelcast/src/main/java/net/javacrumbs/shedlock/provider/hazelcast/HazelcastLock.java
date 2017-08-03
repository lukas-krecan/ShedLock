package net.javacrumbs.shedlock.provider.hazelcast;

import net.javacrumbs.shedlock.core.LockConfiguration;

import java.io.Serializable;
import java.time.Instant;

/**
 * Hazelcast lock entity.
 *
 * It's used to persist lock informations into Hazelcast instances (cluster).
 */
class HazelcastLock implements Serializable {

    private final String name;

    private final Instant lockAtMostUntil;

    private final Instant lockAtLeastUntil;

    /**
     * Unique ID of Hazelcast cluster member.
     * This information is stored to identify the Lock owner and automatically unlock if this member shuddown.
     */
    private final String clusterMemberUuid;

    /**
     * Moment when the lock is expired, so unlockable.
     * The first value of this is {@link #lockAtMostUntil}.
     */
    private Instant unlockTime;

    public HazelcastLock(final String name, final Instant lockAtMostUntil, final Instant lockAtLeastUntil, final String clusterMemberUuid) {
        this.name = name;
        this.lockAtMostUntil = lockAtMostUntil;
        this.lockAtLeastUntil = lockAtLeastUntil;
        this.unlockTime = lockAtMostUntil;
        this.clusterMemberUuid = clusterMemberUuid;
    }

    /**
     * Instanciate {@link HazelcastLock} with {@link LockConfiguration} and Hazelcast member UUID.
     *
     * @param configuration
     * @param clusterMemberUuid
     * @return the new instance of {@link HazelcastLock}.
     */
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
