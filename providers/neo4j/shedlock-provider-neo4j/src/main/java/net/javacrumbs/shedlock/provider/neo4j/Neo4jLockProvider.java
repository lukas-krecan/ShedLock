/**
 * Copyright 2009 the original author or authors.
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
package net.javacrumbs.shedlock.provider.neo4j;

import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import net.javacrumbs.shedlock.support.annotation.Nullable;
import org.neo4j.driver.Driver;

/**
 * Lock provided by Neo4j Graph API. It uses a collection that stores each lock as a node.
 * <ol>
 * <li>
 * Attempts to insert a new lock node. Since lock name has a unique constraint, it fails if the record already exists.
 * As an optimization, we keep in-memory track of created lock nodes.
 * </li>
 * <li>
 * If the insert succeeds (1 node inserted) we have the lock.
 * </li>
 * <li>
 * If the insert failed due to duplicate key or we have skipped the insertion, we will try to update lock node using
 * MATCH (lock:collectionName) WHERE name = $lockName AND lock_until &lt;= $now SET lock_until = $lockUntil, locked_at = $now
 * with some additional explicit node locking
 * </li>
 * <li>
 * If the update succeeded (&gt;1 property updated), we have the lock. If the update failed (&lt;=1 properties updated) somebody else holds the lock
 * or grabbed the lock in a data race caused by Neo4j's read-committed isolation level.
 * </li>
 * <li>
 * When unlocking, lock_until is set to now.
 * </li>
 * </ol>
 */
public class Neo4jLockProvider extends StorageBasedLockProvider {
    public Neo4jLockProvider(@NonNull Driver driver) {
        this(driver, "shedlock", null);
    }

    public Neo4jLockProvider(@NonNull Driver graphDatabaseService, @NonNull String collectionName, @Nullable String databaseName) {
        super(new Neo4jStorageAccessor(graphDatabaseService, collectionName, databaseName));
    }
}
