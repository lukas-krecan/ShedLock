package net.javacrumbs.shedlock.provider.firestore;

import static java.util.Objects.requireNonNull;

import com.google.cloud.firestore.Firestore;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.support.annotation.NonNull;

public class FirestoreLockProvider extends StorageBasedLockProvider {

    public FirestoreLockProvider(@NonNull Firestore firestore) {
        super(new FirestoreStorageAccessor(
                Configuration.builder().withFirestore(firestore).build()));
    }

    public FirestoreLockProvider(@NonNull Configuration configuration) {
        super(new FirestoreStorageAccessor(configuration));
    }

    public static class Configuration {
        private final String collectionName;
        private final FieldNames fieldNames;
        private final Firestore firestore;

        Configuration(@NonNull String collectionName, @NonNull FieldNames fieldNames, @NonNull Firestore firestore) {
            this.collectionName = requireNonNull(collectionName);
            this.fieldNames = requireNonNull(fieldNames);
            this.firestore = requireNonNull(firestore);
        }

        @NonNull
        public String getCollectionName() {
            return collectionName;
        }

        @NonNull
        public FieldNames getFieldNames() {
            return fieldNames;
        }

        @NonNull
        public Firestore getFirestore() {
            return firestore;
        }

        @NonNull
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
