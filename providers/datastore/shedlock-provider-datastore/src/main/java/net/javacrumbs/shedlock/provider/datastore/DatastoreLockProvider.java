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
        private final Fields fields;
        private final Datastore datastore;

        Configuration(String entityName, Fields fields, Datastore datastore) {
            this.entityName = requireNonNull(entityName);
            this.fields = requireNonNull(fields);
            this.datastore = requireNonNull(datastore);
        }

        public String getEntityName() {
            return entityName;
        }

        public Fields getFields() {
            return fields;
        }

        public Datastore getDatastore() {
            return datastore;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String entityName = "lock";
            private Fields fields = new Fields("lock_until", "locked_at", "locked_by");
            private Datastore datastore;

            public Builder withEntityName(String entityName) {
                this.entityName = entityName;
                return this;
            }

            public Builder withFields(Fields fields) {
                this.fields = fields;
                return this;
            }

            public Builder withDatastore(Datastore datastore) {
                this.datastore = datastore;
                return this;
            }

            public Configuration build() {
                return new Configuration(this.entityName, this.fields, this.datastore);
            }
        }
    }

    public record Fields(String lockUntil, String lockedAt, String lockedBy) {
        public Fields(String lockUntil, String lockedAt, String lockedBy) {
            this.lockUntil = requireNonNull(lockUntil);
            this.lockedAt = requireNonNull(lockedAt);
            this.lockedBy = requireNonNull(lockedBy);
        }
    }
}
