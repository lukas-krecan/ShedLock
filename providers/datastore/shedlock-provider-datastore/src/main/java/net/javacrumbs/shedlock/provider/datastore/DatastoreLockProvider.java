package net.javacrumbs.shedlock.provider.datastore;

import com.google.cloud.datastore.Datastore;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;

import static java.util.Objects.requireNonNull;

public class DatastoreLockProvider extends StorageBasedLockProvider {

    public DatastoreLockProvider(Datastore datastore) {
        super(new DatastoreStorageAccessor(Configuration.builder().withDatastore(datastore).build()));
    }

    public DatastoreLockProvider(Configuration configuration) {
        super(new DatastoreStorageAccessor(configuration));
    }

    public static class Configuration {
        private final String entityName;
        private final FieldNames fieldNames;
        private final Datastore datastore;

        Configuration(String entityName, FieldNames fieldNames, Datastore datastore) {
            this.entityName = requireNonNull(entityName);
            this.fieldNames = requireNonNull(fieldNames);
            this.datastore = requireNonNull(datastore);
        }

        public String getEntityName() {
            return entityName;
        }

        public FieldNames getFieldNames() {
            return fieldNames;
        }

        public Datastore getDatastore() {
            return datastore;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String entityName = "lock";
            private FieldNames fieldNames = new FieldNames("lock_until", "locked_at", "locked_by");
            private Datastore datastore;

            public Builder withEntityName(String entityName) {
                this.entityName = entityName;
                return this;
            }

            public Builder withFieldNames(FieldNames fieldNames) {
                this.fieldNames = fieldNames;
                return this;
            }

            public Builder withDatastore(Datastore datastore) {
                this.datastore = datastore;
                return this;
            }

            public Configuration build() {
                return new Configuration(this.entityName, this.fieldNames, this.datastore);
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
