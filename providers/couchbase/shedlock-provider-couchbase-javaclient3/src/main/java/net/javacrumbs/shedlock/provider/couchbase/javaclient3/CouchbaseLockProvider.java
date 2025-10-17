/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.couchbase.javaclient3;

import static net.javacrumbs.shedlock.support.Utils.toIsoString;

import com.couchbase.client.core.error.CasMismatchException;
import com.couchbase.client.core.error.DocumentExistsException;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.java.kv.ReplaceOptions;
import java.time.Instant;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;

/**
 * Distributed lock using CouchbaseDB
 *
 * <p>
 * It uses a collection that contains documents like this:
 *
 * <pre>
 * {
 *    "_id" : "lock name",
 *    "lockUntil" : ISODate("2017-01-07T16:52:04.071Z"),
 *    "lockedAt" : ISODate("2017-01-07T16:52:03.932Z"),
 *    "lockedBy" : "host name"
 * }
 * </pre>
 *
 * lockedAt and lockedBy are just for troubleshooting and are not read by the
 * code
 *
 * <ol>
 * <li>Attempts to insert a new lock record. As an optimization, we keep
 * in-memory track of created lock records. If the record has been inserted,
 * returns lock.
 * <li>We will try to update lock record using filter _id == name AND lock_until
 * &lt;= now
 * <li>If the update succeeded (1 updated document), we have the lock. If the
 * update failed (0 updated documents) somebody else holds the lock Obtaining a
 * optimistic lock in Couchbase Server, Uses the check-and-set (CAS) API to
 * retrieve a CAS revision number CAS number prevents from 2 users to update the
 * same document at the same time.
 * <li>When unlocking, lock_until is set to now.
 * </ol>
 */
public class CouchbaseLockProvider extends StorageBasedLockProvider {
    private static final String LOCK_NAME = "name";
    static final String LOCK_UNTIL = "lockedUntil";
    static final String LOCKED_AT = "lockedAt";
    static final String LOCKED_BY = "lockedBy";

    public CouchbaseLockProvider(Bucket bucket) {
        this(new CouchbaseAccessor(bucket.defaultCollection()));
    }

    public CouchbaseLockProvider(Collection collection) {
        this(new CouchbaseAccessor(collection));
    }

    CouchbaseLockProvider(CouchbaseAccessor couchbaseAccessor) {
        super(couchbaseAccessor);
    }

    private static class CouchbaseAccessor extends AbstractStorageAccessor {

        private final Collection collection;

        CouchbaseAccessor(Collection collection) {
            this.collection = collection;
        }

        @Override
        public boolean insertRecord(LockConfiguration lockConfiguration) {
            JsonObject content = JsonObject.create()
                    .put(LOCK_NAME, lockConfiguration.getName())
                    .put(LOCK_UNTIL, toIsoString(lockConfiguration.getLockAtMostUntil()))
                    .put(LOCKED_AT, toIsoString(ClockProvider.now()))
                    .put(LOCKED_BY, getHostname());

            try {
                collection.insert(lockConfiguration.getName(), content);
            } catch (DocumentExistsException e) {
                return false;
            }
            return true;
        }

        private Instant parse(Object instant) {
            return Instant.parse((String) instant);
        }

        @Override
        public boolean updateRecord(LockConfiguration lockConfiguration) {
            GetResult result = collection.get(lockConfiguration.getName());
            JsonObject document = result.contentAsObject();

            Instant lockUntil = parse(document.get(LOCK_UNTIL));
            Instant now = ClockProvider.now();
            if (lockUntil.isAfter(now)) {
                return false;
            }

            document.put(LOCK_UNTIL, toIsoString(lockConfiguration.getLockAtMostUntil()));
            document.put(LOCKED_AT, toIsoString(now));
            document.put(LOCKED_BY, getHostname());

            try {
                collection.replace(
                        lockConfiguration.getName(),
                        document,
                        ReplaceOptions.replaceOptions().cas(result.cas()));
            } catch (CasMismatchException e) {
                return false;
            }
            return true;
        }

        @Override
        public boolean extend(LockConfiguration lockConfiguration) {
            GetResult result = collection.get(lockConfiguration.getName());
            JsonObject document = result.contentAsObject();

            Instant lockUntil = parse(document.get(LOCK_UNTIL));
            Instant now = ClockProvider.now();
            if (lockUntil.isBefore(now) || !document.get(LOCKED_BY).equals(getHostname())) {
                return false;
            }

            document.put(LOCK_UNTIL, toIsoString(lockConfiguration.getLockAtMostUntil()));

            try {
                collection.replace(
                        lockConfiguration.getName(),
                        document,
                        ReplaceOptions.replaceOptions().cas(result.cas()));
            } catch (CasMismatchException e) {
                return false;
            }
            return true;
        }

        @Override
        public void unlock(LockConfiguration lockConfiguration) {
            GetResult result = collection.get(lockConfiguration.getName());
            JsonObject document = result.contentAsObject();

            document.put(LOCK_UNTIL, toIsoString(lockConfiguration.getUnlockTime()));
            collection.replace(lockConfiguration.getName(), document);
        }
    }
}
