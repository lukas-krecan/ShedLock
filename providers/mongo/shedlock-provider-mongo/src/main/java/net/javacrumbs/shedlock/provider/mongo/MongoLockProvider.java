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
package net.javacrumbs.shedlock.provider.mongo;

import com.mongodb.MongoClient;
import com.mongodb.MongoServerException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Date;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

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
public class MongoLockProvider extends StorageBasedLockProvider {
    static final String LOCK_UNTIL = "lockUntil";
    static final String LOCKED_AT = "lockedAt";
    static final String LOCKED_BY = "lockedBy";
    static final String ID = "_id";

    /**
     * Uses Mongo to coordinate locks
     *
     * @param mongo        Mongo to be used
     * @param databaseName database to be used
     */
    public MongoLockProvider(MongoClient mongo, String databaseName) {
        this(mongo, databaseName, "shedLock");
    }

    /**
     * Uses Mongo to coordinate locks
     *
     * @param mongo          Mongo to be used
     * @param databaseName   database to be used
     * @param collectionName collection to store the locks
     */
    public MongoLockProvider(MongoClient mongo, String databaseName, String collectionName) {
        this(new MongoAccessor(mongo, databaseName, collectionName));
    }

    MongoLockProvider(MongoAccessor mongoAccessor) {
        super(mongoAccessor);
    }

    static class MongoAccessor extends AbstractStorageAccessor {
        private final MongoClient mongo;
        private final String databaseName;
        private final String collectionName;
        private final String hostname;

        MongoAccessor(MongoClient mongo, String databaseName, String collectionName) {
            this.mongo = mongo;
            this.databaseName = databaseName;
            this.collectionName = collectionName;
            this.hostname = getHostname();
        }

        @Override
        public boolean insertRecord(LockConfiguration lockConfiguration) {
            Bson update = combine(
                setOnInsert(LOCK_UNTIL, Date.from(lockConfiguration.getLockAtMostUntil())),
                setOnInsert(LOCKED_AT, now()),
                setOnInsert(LOCKED_BY, hostname)
            );
            try {
                Document result = getCollection().findOneAndUpdate(
                    eq(ID, lockConfiguration.getName()),
                    update,
                    new FindOneAndUpdateOptions().upsert(true)
                );
                return result == null;
            } catch (MongoServerException e) {
                if (e.getCode() == 11000) { // duplicate key
                    // this should not normally happen, but it happened once in tests
                    return false;
                } else {
                    throw e;
                }

            }
        }

        private Date now() {
            return new Date();
        }

        @Override
        public boolean updateRecord(LockConfiguration lockConfiguration) {
            Date now = now();
            Bson update = combine(
                set(LOCK_UNTIL, Date.from(lockConfiguration.getLockAtMostUntil())),
                set(LOCKED_AT, now),
                set(LOCKED_BY, hostname)
            );
            Document result = getCollection().findOneAndUpdate(
                and(eq(ID, lockConfiguration.getName()), lte(LOCK_UNTIL, now)),
                update
            );
            return result != null;
        }

        @Override
        public void unlock(LockConfiguration lockConfiguration) {
            // Set lockUtil to now or lockAtLeastUntil whichever is later
            getCollection().findOneAndUpdate(
                eq(ID, lockConfiguration.getName()),
                combine(set(LOCK_UNTIL, Date.from(lockConfiguration.getUnlockTime())))
            );
        }

        private MongoCollection<Document> getCollection() {
            return mongo.getDatabase(databaseName).getCollection(collectionName);
        }
    }
}
