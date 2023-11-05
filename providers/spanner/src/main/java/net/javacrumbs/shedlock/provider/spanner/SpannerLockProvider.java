package net.javacrumbs.shedlock.provider.spanner;

import com.google.cloud.spanner.DatabaseClient;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.support.Utils;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import static java.util.Objects.requireNonNull;

public class SpannerLockProvider extends StorageBasedLockProvider {


    public SpannerLockProvider(@NonNull DatabaseClient databaseClient) {
        this(new SpannerStorageAccessor(Configuration.builder()
            .withDatabaseClient(databaseClient)
            .build()
        ));
    }

    public SpannerLockProvider(@NonNull Configuration configuration) {
        this(new SpannerStorageAccessor(configuration));
    }

    private SpannerLockProvider(SpannerStorageAccessor spannerStorageAccessor) {
        super(spannerStorageAccessor);
    }


    public static final class Configuration {
        private final DatabaseClient databaseClient;
        private final String hostname;
        private final TableConfiguration tableConfiguration;

        private Configuration(@NonNull DatabaseClient databaseClient) {
            this(builder().withDatabaseClient(databaseClient));
        }

        private Configuration(@NonNull Builder builder) {
            databaseClient = requireNonNull(builder.databaseClient, "databaseClient must be set");
            tableConfiguration = builder.tableConfiguration;
            hostname = builder.hostName;
        }

        public static Builder builder() {
            return new Builder();
        }

        public DatabaseClient getDatabaseClient() {
            return databaseClient;
        }

        public String getHostname() {
            return hostname;
        }

        public TableConfiguration getTableConfiguration() {
            return tableConfiguration;
        }

        public static final class Builder {
            private DatabaseClient databaseClient;
            private String hostName = Utils.getHostname();
            private TableConfiguration tableConfiguration = TableConfiguration.builder()
                .withTableName("shedlock")
                .withLockName("name")
                .withLockedBy("locked_by")
                .withLockedAt("locked_at")
                .withLockUntil("lock_until")
                .build();

            private Builder() {
            }

            public Builder withDatabaseClient(DatabaseClient databaseClient) {
                this.databaseClient = databaseClient;
                return this;
            }

            public Builder withHostName(String hostName) {
                this.hostName = hostName;
                return this;
            }

            public Builder withTableConfiguration(TableConfiguration tableConfiguration) {
                this.tableConfiguration = tableConfiguration;
                return this;
            }

            public Configuration build() {
                return new Configuration(this);
            }
        }
    }

    public static final class TableConfiguration {

        private final String tableName;
        private final String lockName;
        private final String lockUntil;
        private final String lockedAt;
        private final String lockedBy;

        private TableConfiguration(@NonNull Builder builder) {
            tableName = requireNonNull(builder.tableName, "tableName must be set");
            lockName = requireNonNull(builder.lockName, "lockName must be set");
            lockUntil = requireNonNull(builder.lockUntil, "lockUntil must be set");
            lockedAt = requireNonNull(builder.lockedAt, "lockedAt must be set");
            lockedBy = requireNonNull(builder.lockedBy, "lockedBy must be set");
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getTableName() {
            return tableName;
        }

        public String getLockName() {
            return lockName;
        }

        public String getLockUntil() {
            return lockUntil;
        }

        public String getLockedAt() {
            return lockedAt;
        }

        public String getLockedBy() {
            return lockedBy;
        }


        public static final class Builder {
            private String tableName;
            private String lockName;
            private String lockUntil;
            private String lockedAt;
            private String lockedBy;

            private Builder() {
            }

            public Builder withTableName(String tableName) {
                this.tableName = tableName;
                return this;
            }

            public Builder withLockName(String lockNameColumn) {
                this.lockName = lockNameColumn;
                return this;
            }

            public Builder withLockUntil(String lockUntilColumn) {
                this.lockUntil = lockUntilColumn;
                return this;
            }

            public Builder withLockedAt(String lockedAtColumn) {
                this.lockedAt = lockedAtColumn;
                return this;
            }

            public Builder withLockedBy(String lockedByColumn) {
                this.lockedBy = lockedByColumn;
                return this;
            }

            public TableConfiguration build() {
                return new TableConfiguration(this);
            }

        }
    }
}
