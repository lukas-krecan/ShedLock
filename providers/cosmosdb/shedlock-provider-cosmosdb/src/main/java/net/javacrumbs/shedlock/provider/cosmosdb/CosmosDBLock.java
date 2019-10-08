package net.javacrumbs.shedlock.provider.cosmosdb;

import com.azure.data.cosmos.CosmosContainer;
import com.azure.data.cosmos.CosmosItemResponse;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;

import java.io.IOException;
import java.util.Date;
import java.util.Objects;

public class CosmosDBLock implements SimpleLock {
    private final CosmosContainer container;
    private final LockConfiguration lockConfiguration;
    private final String lockGroup;
    private String hostname;

    CosmosDBLock(CosmosContainer container, LockConfiguration lockConfiguration, String lockGroup, String hostname) {
        this.container = container;
        this.lockConfiguration = lockConfiguration;
        this.lockGroup = lockGroup;
        this.hostname = hostname;
    }

    @Override
    public void unlock() {
        Date unlockTime = Date.from(lockConfiguration.getUnlockTime());
        Date lockAtLeastUntil = Date.from(lockConfiguration.getLockAtLeastUntil());
        Lock unlocked = new Lock(lockConfiguration.getName(), unlockTime, lockAtLeastUntil, hostname, lockGroup);
        container.getItem(lockConfiguration.getName(), lockGroup)
                .replace(unlocked)
                .block();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CosmosDBLock that = (CosmosDBLock) o;
        return Objects.equals(lockConfiguration, that.lockConfiguration) &&
                Objects.equals(lockGroup, that.lockGroup) &&
                Objects.equals(hostname, that.hostname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lockConfiguration, lockGroup, hostname);
    }

    private Lock getObject(CosmosItemResponse response) {
        try {
            return response.properties().getObject(Lock.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
