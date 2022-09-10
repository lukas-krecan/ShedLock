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
package net.javacrumbs.shedlock.provider.couchbase.javaclient;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.error.CASMismatchException;
import com.couchbase.client.java.error.DocumentAlreadyExistsException;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import java.time.Instant;

import static net.javacrumbs.shedlock.support.Utils.toIsoString;

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
 * @deprecated Please migrate to shedlock-provider-couchbase-javaclient3
 */
@Deprecated
public class CouchbaseLockProvider extends StorageBasedLockProvider {
    private static final String LOCK_NAME = "name";
    static final String LOCK_UNTIL = "lockedUntil";
    static final String LOCKED_AT = "lockedAt";
    static final String LOCKED_BY = "lockedBy";

    public CouchbaseLockProvider(Bucket bucket) {
        this(new CouchbaseLockProvider.CouchbaseAccessor(bucket));
    }

    CouchbaseLockProvider(CouchbaseLockProvider.CouchbaseAccessor couchbaseAccessor) {
        super(couchbaseAccessor);
    }

    private static class CouchbaseAccessor extends AbstractStorageAccessor {

        private final Bucket bucket;

        CouchbaseAccessor(Bucket bucket) {
            this.bucket = bucket;
        }

        @Override
        public boolean insertRecord(@NonNull LockConfiguration lockConfiguration) {
            try {

                JsonObject content = JsonObject.empty();
                content.put(LOCK_NAME, lockConfiguration.getName());
                content.put(LOCK_UNTIL, toIsoString(lockConfiguration.getLockAtMostUntil()));
                content.put(LOCKED_AT, toIsoString(ClockProvider.now()));
                content.put(LOCKED_BY, getHostname());
                JsonDocument document = JsonDocument.create(lockConfiguration.getName(), content);

                bucket.insert(document);
                return true;

            } catch (DocumentAlreadyExistsException e) {
                return false;
            }

        }

        private Instant parse(Object instant) {
            return Instant.parse((String) instant);
        }

        @Override
        public boolean updateRecord(@NonNull LockConfiguration lockConfiguration) {
            try {
                JsonDocument document = bucket.get(lockConfiguration.getName());
                Instant lockUntil = parse(document.content().get(LOCK_UNTIL));
                Instant now = ClockProvider.now();
                if (lockUntil.isAfter(now)) {
                    return false;
                }

                document.content().put(LOCK_UNTIL, toIsoString(lockConfiguration.getLockAtMostUntil()));
                document.content().put(LOCKED_AT, toIsoString(now));
                document.content().put(LOCKED_BY, getHostname());

                bucket.replace(document);
                return true;

            }catch (CASMismatchException e){
                return false;
            }

        }

        @Override
        public boolean extend(@NonNull LockConfiguration lockConfiguration) {
            try {
                JsonDocument document = bucket.get(lockConfiguration.getName());
                Instant lockUntil = parse(document.content().get(LOCK_UNTIL));
                Instant now = ClockProvider.now();
                if (lockUntil.isBefore(now) || !document.content().get(LOCKED_BY).equals(getHostname())) {
                    return false;
                }

                document.content().put(LOCK_UNTIL, toIsoString(lockConfiguration.getLockAtMostUntil()));

                bucket.replace(document);
                return true;

            } catch (CASMismatchException e) {
                return false;
            }
        }

        @Override
        public void unlock(@NonNull LockConfiguration lockConfiguration) {
            JsonDocument document = bucket.get(lockConfiguration.getName());
            document.content().put(LOCK_UNTIL, toIsoString(lockConfiguration.getUnlockTime()));
            bucket.replace(document);
        }
    }

}

