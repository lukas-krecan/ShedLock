package net.javacrumbs.shedlock.provider.spanner;

import static java.util.Objects.requireNonNull;

import com.google.cloud.spanner.DatabaseClient;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.support.Utils;
import net.javacrumbs.shedlock.support.annotation.NonNull;

/**
 * A lock provider for Google Cloud Spanner.
 * This provider uses Spanner as the backend storage for the locks.
 */
public class SpannerLockProvider extends StorageBasedLockProvider {

    /**
     * Constructs a new {@code SpannerLockProvider} with the provided {@link DatabaseClient}.
     *
     * @param databaseClient the client for interacting with Google Cloud Spanner.
     */
    public SpannerLockProvider(@NonNull DatabaseClient databaseClient) {
        this(Configuration.builder().withDatabaseClient(databaseClient).build());
    }

    /**
     * Constructs a new {@code SpannerLockProvider} using the specified configuration.
     *
     * @param configuration configuration for the provider.
     */
    public SpannerLockProvider(@NonNull Configuration configuration) {
        super(new SpannerStorageAccessor(configuration));
    }

    /**
     * Configuration class for {@code SpannerLockProvider}.
     * It holds configuration details required to create an instance of {@code SpannerLockProvider}.
     */
    public static final class Configuration {
        private final DatabaseClient databaseClient;
        private final String hostname;
        private final TableConfiguration tableConfiguration;

        /**
         * Constructs a {@code Configuration} with the builder pattern.
         *
         * @param builder the {@code Builder} object.
         */
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

        /**
         * Builder for {@link Configuration}. It provides defaults for table configuration and hostname.
         * A default {@link TableConfiguration} and host name are used if not explicitly specified.
         */
        public static final class Builder {
            private DatabaseClient databaseClient;

            // Default host name is obtained from the Utils class if not specified.
            private String hostName = Utils.getHostname();

            // Default table configuration if not specified by the user of the builder.
            private TableConfiguration tableConfiguration = TableConfiguration.builder()
                    .withTableName("shedlock")
                    .withLockName("name")
                    .withLockedBy("locked_by")
                    .withLockedAt("locked_at")
                    .withLockUntil("lock_until")
                    .build();

            private Builder() {}

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

            /**
             * Builds the {@link Configuration} with the provided parameters. If the table configuration or
             * hostname are not set, it will default to a pre-defined table configuration for ShedLock and
             * the local hostname.
             *
             * @return A new instance of {@link Configuration} with the set parameters.
             */
            public Configuration build() {
                return new Configuration(this);
            }
        }
    }

    /**
     * Class representing the table configuration for the lock provider.
     */
    public static final class TableConfiguration {

        private final String tableName;
        private final String lockName;
        private final String lockUntil;
        private final String lockedAt;
        private final String lockedBy;

        /**
         * Constructs a {@code TableConfiguration} using the builder pattern.
         *
         * @param builder the {@code Builder} for the table configuration.
         */
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

        /**
         * Builder for creating {@code TableConfiguration} instances.
         */
        public static final class Builder {
            private String tableName;
            private String lockName;
            private String lockUntil;
            private String lockedAt;
            private String lockedBy;

            private Builder() {}

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

            /**
             * Builds the {@code TableConfiguration} object.
             *
             * @return a new {@code TableConfiguration} instance.
             */
            public TableConfiguration build() {
                return new TableConfiguration(this);
            }
        }
    }
}
