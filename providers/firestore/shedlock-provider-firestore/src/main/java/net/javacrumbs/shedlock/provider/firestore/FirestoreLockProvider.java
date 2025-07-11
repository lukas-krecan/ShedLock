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
package net.javacrumbs.shedlock.provider.firestore;

import static java.util.Objects.requireNonNull;

import com.google.cloud.firestore.Firestore;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;

/**
 * Distributed lock using Google Cloud Firestore. It uses a collection that contains documents like this:
 *
 * <pre>
 * {
 *    "name" : "lock name",
 *    "lockUntil" : Timestamp("2017-01-07T16:52:04.071Z"),
 *    "lockedAt" : Timestamp("2017-01-07T16:52:03.932Z"),
 *    "lockedBy" : "host name"
 * }
 * </pre>
 *
 * lockedAt and lockedBy are just for troubleshooting and are not read by the code
 *
 * <ol>
 * <li>Attempts to insert a new lock record. As an optimization, we keep
 * in-memory track of created lock records. If the record has been inserted,
 * returns lock.
 * <li>We will try to update lock record using filter name == lockName AND lockUntil
 * &lt;= now
 * <li>If the update succeeded (1 updated document), we have the lock. If the
 * update failed (0 updated documents) somebody else holds the lock
 * <li>When unlocking, lockUntil is set to now.
 * </ol>
 */
public class FirestoreLockProvider extends StorageBasedLockProvider {

    public FirestoreLockProvider(Firestore firestore) {
        super(new FirestoreStorageAccessor(
                Configuration.builder().withFirestore(firestore).build()));
    }

    public FirestoreLockProvider(Configuration configuration) {
        super(new FirestoreStorageAccessor(configuration));
    }

    public static class Configuration {
        private final String collectionName;
        private final FieldNames fieldNames;
        private final Firestore firestore;

        Configuration(String collectionName, FieldNames fieldNames, Firestore firestore) {
            this.collectionName = requireNonNull(collectionName);
            this.fieldNames = requireNonNull(fieldNames);
            this.firestore = requireNonNull(firestore);
        }

        public String getCollectionName() {
            return collectionName;
        }

        public FieldNames getFieldNames() {
            return fieldNames;
        }

        public Firestore getFirestore() {
            return firestore;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String collectionName = "shedlock";
            private FieldNames fieldNames = new FieldNames("lockUntil", "lockedAt", "lockedBy");
            private Firestore firestore;

            public Builder withCollectionName(String collectionName) {
                this.collectionName = collectionName;
                return this;
            }

            public Builder withFieldNames(FieldNames fieldNames) {
                this.fieldNames = fieldNames;
                return this;
            }

            public Builder withFirestore(Firestore firestore) {
                this.firestore = firestore;
                return this;
            }

            public Configuration build() {
                return new Configuration(this.collectionName, this.fieldNames, this.firestore);
            }
        }
    }

    public record FieldNames(String lockUntil, String lockedAt, String lockedBy) {
        public FieldNames(String lockUntil, String lockedAt, String lockedBy) {
            this.lockUntil = requireNonNull(lockUntil);
            this.lockedAt = requireNonNull(lockedAt);
            this.lockedBy = requireNonNull(lockedBy);
        }
    }
}
