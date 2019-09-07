/**
 * Copyright 2009-2018 the original author or authors.
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
package net.javacrumbs.shedlock.support;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

import java.time.Instant;
import java.util.Optional;

/**
 * Distributed lock using abstract storage
 * <p>
 * It uses a table/collection that contains ID = lock name and a field locked_until.
 * <ol>
 * <li>
 * Attempts to insert a new lock record. As an optimization, we keep in-memory track of created lock records. If the record
 * has been inserted, returns lock.
 * </li>
 * <li>
 * We will try to update lock record using filter ID == name AND lock_until &lt;= now
 * </li>
 * <li>
 * If the update succeeded (1 updated row/document), we have the lock. If the update failed (0 updated documents) somebody else holds the lock
 * </li>
 * <li>
 * When unlocking, lock_until is set to now.
 * </li>
 * </ol>
 */
public class StorageBasedLockProvider implements LockProvider {
    private final StorageAccessor storageAccessor;
    private final LockRecordRegistry lockRecordRegistry = new LockRecordRegistry();

    protected StorageBasedLockProvider(StorageAccessor storageAccessor) {
        this.storageAccessor = storageAccessor;
    }

    /**
     * Clears cache of existing lock records.
     */
    public void clearCache() {
        lockRecordRegistry.clear();
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        boolean lockObtained = doLock(lockConfiguration);
        if (lockObtained) {
            return Optional.of(new StorageLock(lockConfiguration, storageAccessor));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Sets lockUntil according to LockConfiguration if current lockUntil &lt;= now
     */
    protected boolean doLock(LockConfiguration lockConfiguration) {
        String name = lockConfiguration.getName();

        if (!lockRecordRegistry.lockRecordRecentlyCreated(name)) {
            // create record in case it does not exist yet
            if (storageAccessor.insertRecord(lockConfiguration)) {
                lockRecordRegistry.addLockRecord(name);
                // we were able to create the record, we have the lock
                return true;
            }
            // we were not able to create the record, it already exists, let's put it to the cache so we do not try again
            lockRecordRegistry.addLockRecord(name);
        }

        // let's try to update the record, if successful, we have the lock
        return storageAccessor.updateRecord(lockConfiguration);
    }

    private static class StorageLock implements SimpleLock {
        private final LockConfiguration lockConfiguration;
        private final StorageAccessor storageAccessor;

        StorageLock(LockConfiguration lockConfiguration, StorageAccessor storageAccessor) {
            this.lockConfiguration = lockConfiguration;
            this.storageAccessor = storageAccessor;
        }

        @Override
        public void unlock() {
            storageAccessor.unlock(lockConfiguration);
        }

        @Override
        public Optional<SimpleLock> extend(Instant lockAtMostUntil, Instant lockAtLeastUntil) {
            LockConfiguration newConfig = new LockConfiguration(lockConfiguration.getName(), lockAtMostUntil, lockAtLeastUntil);
            if (storageAccessor.extend(newConfig)) {
                return Optional.of(new StorageLock(newConfig, storageAccessor));
            } else {
                return Optional.empty();
            }
        }
    }

}
