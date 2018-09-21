package net.javacrumbs.shedlock.provider.couchbase;
/**
 * Copyright 2009-2017 the original author or authors.
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

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.error.CASMismatchException;
import com.couchbase.client.java.error.DocumentAlreadyExistsException;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Distributed lock using CouchbaseDB
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
 * Obtaining a optimistic lock in Couchbase Server, Uses the check-and-set (CAS) API to retrieve a CAS revision number
 * CAS number prevents from 2 users to update the same document at the same time.
 * </li>
 * <li>
 * When unlocking, lock_until is set to now.
 * </li>
 * </ol>
 */
public class CouchbaseLockProvider extends StorageBasedLockProvider {

    static final String LOCK_NAME = "name";
    static final String LOCK_UNTIL = "lockedUntil";
    static final String LOCKED_AT = "lockedAt";
    static final String LOCKED_BY = "lockedBy";

    public CouchbaseLockProvider (Bucket bucket) {
        this(new CouchbaseLockProvider.CouchbaseAccessor(bucket));
    }

    CouchbaseLockProvider(CouchbaseLockProvider.CouchbaseAccessor couchbaseAccessor) {
        super(couchbaseAccessor);
    }

    static class CouchbaseAccessor extends AbstractStorageAccessor {

        private final Bucket bucket;

        CouchbaseAccessor(Bucket bucket) {
            this.bucket = bucket;
        }

        @Override
        public boolean insertRecord(LockConfiguration lockConfiguration) {
            try {

                JsonObject content = JsonObject.empty();
                content.put(LOCK_NAME, lockConfiguration.getName());
                content.put(LOCK_UNTIL, lockUntil(lockConfiguration.getLockAtMostUntil()));
                content.put(LOCKED_AT, now());
                content.put(LOCKED_BY, getHostname());
                JsonDocument document = JsonDocument.create(lockConfiguration.getName(), content);

                bucket.insert(document);
                return true;

            } catch (DocumentAlreadyExistsException e) {
                return false;
            }

        }


        @Override
        public boolean updateRecord(LockConfiguration lockConfiguration)
        {
            try {
                JsonDocument document = bucket.get(lockConfiguration.getName());
                LocalDateTime lockUntil = LocalDateTime.parse((String) document.content().get(LOCK_UNTIL));
                if (lockUntil.isAfter(LocalDateTime.parse(now()))) {
                    return false;
                }

                document.content().put(LOCK_UNTIL, lockUntil(lockConfiguration.getLockAtMostUntil()));
                document.content().put(LOCKED_AT, now());
                document.content().put(LOCKED_BY, getHostname());

                bucket.replace(document);
                return true;

            }catch (CASMismatchException e){
                return false;
            }

        }

        @Override
        public void unlock(LockConfiguration lockConfiguration) {
            JsonDocument document = bucket.get(lockConfiguration.getName());
            document.content().put(LOCK_UNTIL, lockUntil(lockConfiguration.getUnlockTime()));
            document.content().put(LOCKED_AT,  now());
            document.content().put(LOCKED_BY,  getHostname());
            bucket.replace(document);
        }

        private String now() {
            LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
            DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            return localDateTime.format(timeFormatter);
        }

        private String lockUntil(Instant lockAtMostUntil) {
            LocalDateTime localDateTime = LocalDateTime.ofInstant(lockAtMostUntil, ZoneId.systemDefault());
            DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            return localDateTime.format(timeFormatter);
        }

    }

}

