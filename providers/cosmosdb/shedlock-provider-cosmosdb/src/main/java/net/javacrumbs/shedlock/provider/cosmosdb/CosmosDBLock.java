/**
 * Copyright 2009-2019 the original author or authors.
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
package net.javacrumbs.shedlock.provider.cosmosdb;

import com.azure.data.cosmos.CosmosContainer;
import com.azure.data.cosmos.CosmosItemResponse;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;

import java.io.IOException;
import java.util.Date;
import java.util.Objects;

class CosmosDBLock implements SimpleLock {
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
