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
package net.javacrumbs.shedlock.provider.mongo;

import com.mongodb.MongoServerException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.Utils;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.util.Optional;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;

/**
 * Distributed lock using MongoDB &gt;= 2.6. Requires mongo-java-driver &gt; 3.4.0
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
 *
 * lockedAt and lockedBy are just for troubleshooting and are not read by the code
 *
 * <ol>
 * <li>
 * Attempts to insert a new lock record. As an optimization, we keep in-memory track of created lock records. If the record
 * has been inserted, returns lock.
 * </li>
 * <li>
 * We will try to update lock record using filter _id == name AND lock_until &lt;= now
 * </li>
 * <li>
 * If the update succeeded (1 updated document), we have the lock. If the update failed (0 updated documents) somebody else holds the lock
 * </li>
 * <li>
 * When unlocking, lock_until is set to now.
 * </li>
 * </ol>
 */
public class MongoLockProvider implements ExtensibleLockProvider {
    static final String LOCK_UNTIL = "lockUntil";
    static final String LOCKED_AT = "lockedAt";
    static final String LOCKED_BY = "lockedBy";
    static final String ID = "_id";
    static final String DEFAULT_SHEDLOCK_COLLECTION_NAME = "shedLock";

    private final String hostname;
    private final MongoCollection<Document> collection;

    /**
     * Uses Mongo to coordinate locks
     */
    public MongoLockProvider(@NonNull MongoDatabase mongoDatabase) {
        this(mongoDatabase.getCollection(DEFAULT_SHEDLOCK_COLLECTION_NAME));
    }

    /**
     * Uses Mongo to coordinate locks
     *
     * @param collection Mongo collection to be used
     */
    public MongoLockProvider(@NonNull MongoCollection<Document> collection) {
        this.collection = collection;
        this.hostname = Utils.getHostname();
    }


    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        Instant now = now();
        Bson update = combine(
            set(LOCK_UNTIL, lockConfiguration.getLockAtMostUntil()),
            set(LOCKED_AT, now),
            set(LOCKED_BY, hostname)
        );
        try {
            // There are three possible situations:
            // 1. The lock document does not exist yet - it is inserted - we have the lock
            // 2. The lock document exists and lockUtil <= now - it is updated - we have the lock
            // 3. The lock document exists and lockUtil > now - Duplicate key exception is thrown
            getCollection().findOneAndUpdate(
                and(eq(ID, lockConfiguration.getName()), lte(LOCK_UNTIL, now)),
                update,
                new FindOneAndUpdateOptions().upsert(true)
            );
            return Optional.of(new MongoLock(lockConfiguration, this));
        } catch (MongoServerException e) {
            if (e.getCode() == 11000) { // duplicate key
                //Upsert attempts to insert when there were no filter matches.
                //This means there was a lock with matching ID with lockUntil > now.
                return Optional.empty();
            } else {
                throw e;
            }
        }
    }

    private Optional<SimpleLock> extend(LockConfiguration lockConfiguration) {
        Instant now = now();
        Bson update = set(LOCK_UNTIL, lockConfiguration.getLockAtMostUntil());

        Document updatedDocument = getCollection().findOneAndUpdate(
            and(
                eq(ID, lockConfiguration.getName()),
                gt(LOCK_UNTIL, now),
                eq(LOCKED_BY, hostname)
            ),
            update
        );
        if (updatedDocument != null) {
            return Optional.of(new MongoLock(lockConfiguration, this));
        } else {
            return Optional.empty();
        }
    }

    private void unlock(LockConfiguration lockConfiguration) {
        // Set lockUtil to now or lockAtLeastUntil whichever is later
        getCollection().findOneAndUpdate(
            eq(ID, lockConfiguration.getName()),
            combine(set(LOCK_UNTIL, lockConfiguration.getUnlockTime()))
        );
    }

    private MongoCollection<Document> getCollection() {
        return collection;
    }

    private Instant now() {
        return ClockProvider.now();
    }

    private static final class MongoLock extends AbstractSimpleLock {
        private final MongoLockProvider mongoLockProvider;

        private MongoLock(LockConfiguration lockConfiguration, MongoLockProvider mongoLockProvider) {
            super(lockConfiguration);
            this.mongoLockProvider = mongoLockProvider;
        }

        @Override
        public void doUnlock() {
            mongoLockProvider.unlock(lockConfiguration);
        }

        @Override
        public Optional<SimpleLock> doExtend(LockConfiguration newLockConfiguration) {
            return mongoLockProvider.extend(newLockConfiguration);
        }
    }
}
