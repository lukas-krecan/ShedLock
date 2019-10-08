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
import com.azure.data.cosmos.CosmosStoredProcedureRequestOptions;
import com.azure.data.cosmos.PartitionKey;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.Utils;

import java.util.Optional;

/**
 * Distributed lock using CosmosDB on Azure.
 * <p>
 * It uses a collection that contains documents like this:
 * <pre>
 * {
 *    "_id" : "lock name",
 *    "lockUntil" : ISODate("2017-01-07T16:52:04.071Z"),
 *    "lockedAt" : ISODate("2017-01-07T16:52:03.932Z"),
 *    "lockedBy" : "host name"
 * }
 * </pre>
 * <p>
 * lockedAt and lockedBy are just for troubleshooting and are not read by the code
 *
 * <ol>
 * <li>
 * Attempts to insert a new lock record. As an optimization, we keep in-memory track of created lock records. If the record
 * has been inserted, returns lock.
 * </li>
 * <li>
 * We will invoke a stored procedure (checkLockAndAcquire.js) to check if the locke exists and update table.
 * </li>
 * <li>
 * If the stored procedure returns false, it means that there isn't a lock, so, we have the lock. Otherwise somebody else holds the lock
 * </li>
 * <li>
 * When unlocking, lock_until is set to now.
 * </li>
 * </ol>
 */
public class CosmosDBProvider implements LockProvider {

    public static final String ACQUIRE_LOCK_STORED_PROCEDURE = "acquireLock";
    private final CosmosContainer container;
    private String lockGroup;
    private final String hostname;

    public CosmosDBProvider(CosmosContainer container, String lockGroup) {
        this.container = container;
        this.lockGroup = lockGroup;
        this.hostname = Utils.getHostname();
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        PartitionKey partitionKey = new PartitionKey(lockGroup);
        CosmosStoredProcedureRequestOptions options = new CosmosStoredProcedureRequestOptions().partitionKey(partitionKey);
        long now = System.currentTimeMillis();
        Object[] procedureParams = getProcedureParams(lockConfiguration, now);
        Boolean hasLock = container.getScripts()
                .getStoredProcedure(ACQUIRE_LOCK_STORED_PROCEDURE)
                .execute(procedureParams, options)
                .map(response -> Boolean.valueOf(response.responseAsString()))
                .block();

        if (!hasLock) {
            return Optional.of(new CosmosDBLock(container, lockConfiguration, lockGroup, hostname));
        } else {
            return Optional.empty();
        }
    }

    protected Object[] getProcedureParams(LockConfiguration lockConfiguration, long now) {
        return new Object[]{lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil().toEpochMilli(), now, hostname, lockGroup};
    }

}