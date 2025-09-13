package net.javacrumbs.shedlock.provider.firestore;

import static java.util.Objects.requireNonNull;

import com.google.cloud.firestore.Firestore;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;

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

    public record FieldNames(String lockUntil, String lockedAt, String lockedBy) {}
}
