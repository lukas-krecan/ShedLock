package net.javacrumbs.shedlock.provider.hazelcast;

import net.javacrumbs.shedlock.core.LockConfiguration;

import java.io.Serializable;
import java.time.Instant;

/**
 * Hazelcast lock entity.
 * <p>
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
    private final Instant timeToLive;

    public HazelcastLock(final String name, final Instant lockAtMostUntil, final Instant lockAtLeastUntil, final Instant timeToLive, final String clusterMemberUuid) {
        this.name = name;
        this.lockAtMostUntil = lockAtMostUntil;
        this.lockAtLeastUntil = lockAtLeastUntil;
        this.timeToLive = timeToLive;
        this.clusterMemberUuid = clusterMemberUuid;
    }

    /**
     * Instantiate {@link HazelcastLock} with {@link LockConfiguration} and Hazelcast member UUID.
     *
     * @param configuration
     * @param clusterMemberUuid
     * @return the new instance of {@link HazelcastLock}.
     */
    public static HazelcastLock fromConfigurationWhereTtlIsUntilTime(final LockConfiguration configuration, final String clusterMemberUuid) {
        return new HazelcastLock(configuration.getName(), configuration.getLockAtMostUntil(), configuration.getLockAtLeastUntil(), configuration.getLockAtMostUntil(), clusterMemberUuid);
    }

    /**
     * Copy an existing {@link HazelcastLock} and change its time to live.
     *
     * @param lock
     * @return the new instance of {@link HazelcastLock}.
     */
    public static HazelcastLock fromLockWhereTtlIsReduceToLeastTime(final HazelcastLock lock) {
        return new HazelcastLock(lock.name, lock.lockAtMostUntil, lock.lockAtLeastUntil, lock.lockAtLeastUntil, lock.clusterMemberUuid);
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

    public Instant getTimeToLive() {
        return timeToLive;
    }

    @Override
    public String toString() {
        return "HazelcastLock{" +
                "name='" + name + '\'' +
                ", lockAtMostUntil=" + lockAtMostUntil +
                ", lockAtLeastUntil=" + lockAtLeastUntil +
                ", clusterMemberUuid='" + clusterMemberUuid + '\'' +
                ", timeToLive=" + timeToLive +
                '}';
    }

}
