package net.javacrumbs.shedlock.provider.firestore;

import static java.util.Objects.requireNonNull;

import com.google.cloud.firestore.Firestore;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;

/**
 * It uses a collection that contains documents like this:
 *
 * <pre>
 * {
 *    "name" : "lock name",
 *    "lockUntil" :  {
 *      "type":   "date",
 *      "format": "epoch_millis"
 *    },
 *    "lockedAt" : {
 *      "type":   "date",
 *      "format": "epoch_millis"
 *    }:
 *    "lockedBy" : "hostname"
 * }
 * </pre>
 *
 * <p>
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
 * update failed (0 updated documents) somebody else holds the lock
 * <li>When unlocking, lock_until is set to now.
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

        Configuration(String entityName, FieldNames fieldNames, Firestore firestore) {
            this.collectionName = requireNonNull(entityName);
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
            private String collectionName = "lock";
            private FieldNames fieldNames = new FieldNames("lock_until", "locked_at", "locked_by");
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
