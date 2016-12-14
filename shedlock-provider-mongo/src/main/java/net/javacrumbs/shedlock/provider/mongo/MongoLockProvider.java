/**
 * Copyright 2009-2016 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.mongo;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.bson.Document;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Optional;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;
import static com.mongodb.client.model.Updates.setOnInsert;

/**
 * Distributed lock using MongoDB > 2.6
 */
public class MongoLockProvider implements LockProvider {
    static final String LOCK_UNTIL = "lockUntil";
    static final String LOCKED_AT = "lockedAt";
    static final String LOCKED_BY = "lockedBy";
    static final String ID = "_id";
    private final MongoClient mongo;
    private final String databaseName;
    private final String collectionName;
    private final String hostname;

    public MongoLockProvider(MongoClient mongo, String databaseName, String collectionName) {
        this.mongo = mongo;
        this.databaseName = databaseName;
        this.collectionName = collectionName;
        this.hostname = getHostname();
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        boolean lockObtained = doLock(lockConfiguration);
        if (lockObtained) {
            return Optional.of(new MongoLock(lockConfiguration));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Sets lockUntil according to LockConfiguration if current lockUntil <= now
     */
    protected boolean doLock(LockConfiguration lockConfiguration) {
        String name = lockConfiguration.getName();

        // create document in case it does not exist yet
        getCollection().findOneAndUpdate(eq(ID, name), setOnInsert(LOCK_UNTIL, now()), new FindOneAndUpdateOptions().upsert(true));

        // update document
        Document result = getCollection().findOneAndUpdate(
            and(eq(ID, name), lte(LOCK_UNTIL, now())),
            combine(
                set(LOCK_UNTIL, Date.from(lockConfiguration.getLockUntil())),
                set(LOCKED_AT, now()),
                set(LOCKED_BY, hostname)
            )
        );
        return result != null;
    }

    private Date now() {
        return new Date();
    }

    private MongoCollection<Document> getCollection() {
        return mongo.getDatabase(databaseName).getCollection(collectionName);
    }

    private static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    private class MongoLock implements SimpleLock {
        private final LockConfiguration lockConfiguration;

        public MongoLock(LockConfiguration lockConfiguration) {
            this.lockConfiguration = lockConfiguration;
        }

        @Override
        public void unlock() {
            // Set lockUtil to now
            getCollection().findOneAndUpdate(
                eq(ID, lockConfiguration.getName()),
                combine(set(LOCK_UNTIL, now()))
            );
        }
    }
}
