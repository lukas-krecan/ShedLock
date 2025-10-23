/*
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
package net.javacrumbs.shedlock.provider.r2dbc;

import static java.util.Objects.requireNonNull;

import io.r2dbc.spi.ConnectionFactory;
import java.util.TimeZone;
import net.javacrumbs.shedlock.provider.sql.DatabaseProduct;
import net.javacrumbs.shedlock.provider.sql.SqlConfiguration;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import org.jspecify.annotations.Nullable;

/**
 * Lock provided by plain R2DBC SPI. It uses a table that contains lock_name and
 * locked_until.
 *
 * <ol>
 * <li>Attempts to insert a new lock record. Since lock name is a primary key,
 * it fails if the record already exists. As an optimization, we keep in-memory
 * track of created lock records.
 * <li>If the insert succeeds (1 inserted row) we have the lock.
 * <li>If the insert failed due to duplicate key or we have skipped the
 * insertion, we will try to update lock record using UPDATE tableName SET
 * lock_until = :lockUntil WHERE name = :lockName AND lock_until &lt;= :now
 * <li>If the update succeeded (1 updated row), we have the lock. If the update
 * failed (0 updated rows) somebody else holds the lock
 * <li>When unlocking, lock_until is set to now.
 * </ol>
 */
public class R2dbcLockProvider extends StorageBasedLockProvider {
    public R2dbcLockProvider(ConnectionFactory connectionFactory) {
        this(Configuration.builder(connectionFactory).build());
    }

    public R2dbcLockProvider(ConnectionFactory connectionFactory, String tableName) {
        this(Configuration.builder(connectionFactory).withTableName(tableName).build());
    }

    public R2dbcLockProvider(Configuration configuration) {
        super(new R2dbcStorageAccessor(configuration));
    }

    public static final class Configuration extends SqlConfiguration {
        private final ConnectionFactory connectionFactory;

        Configuration(
                ConnectionFactory connectionFactory,
                boolean dbUpperCase,
                @Nullable DatabaseProduct databaseProduct,
                String tableName,
                ColumnNames columnNames,
                String lockedByValue,
                boolean useDbTime,
                boolean forceUtcTimeZone) {
            super(
                    databaseProduct,
                    dbUpperCase,
                    tableName,
                    !useDbTime && forceUtcTimeZone ? TimeZone.getTimeZone("UTC") : null,
                    columnNames,
                    lockedByValue,
                    useDbTime);
            this.connectionFactory = requireNonNull(connectionFactory, "connectionFactory can not be null");
        }

        public ConnectionFactory getConnectionFactory() {
            return connectionFactory;
        }

        @Override
        public DatabaseProduct getDatabaseProduct() {
            if (super.getDatabaseProduct() != null) {
                return super.getDatabaseProduct();
            }

            return switch (connectionFactory.getMetadata().getName()) {
                case "Microsoft SQL Server" -> DatabaseProduct.SQL_SERVER;
                case "MySQL", "Jasync-MySQL" -> DatabaseProduct.MY_SQL;
                case "MariaDB" -> DatabaseProduct.MARIA_DB;
                case "Oracle Database" -> DatabaseProduct.ORACLE;
                case "PostgreSQL" -> DatabaseProduct.POSTGRES_SQL;
                case "H2" -> DatabaseProduct.H2;
                default -> DatabaseProduct.UNKNOWN;
            };
        }

        public static Configuration.Builder builder(ConnectionFactory connectionFactory) {
            return new Configuration.Builder(connectionFactory);
        }

        public static final class Builder extends SqlConfigurationBuilder<Builder> {
            private final ConnectionFactory connectionFactory;
            private boolean forceUtcTimeZone;

            Builder(ConnectionFactory connectionFactory) {
                this.connectionFactory = connectionFactory;
            }

            /**
             * Enforces UTC times. When the useDbTime() is not set, the timestamps are sent to the DB in the JVM default timezone.
             * If your server is not in UTC and you are not using TIMEZONE WITH TIMESTAMP or an equivalent, the TZ information
             * may be lost. For example in Postgres.
             */
            public Builder forceUtcTimeZone(boolean forceDbTime) {
                this.forceUtcTimeZone = forceDbTime;
                return this;
            }

            public Configuration build() {
                return new Configuration(
                        connectionFactory,
                        dbUpperCase,
                        databaseProduct,
                        tableName,
                        columnNames,
                        lockedByValue,
                        useDbTime,
                        forceUtcTimeZone);
            }
        }
    }
}
