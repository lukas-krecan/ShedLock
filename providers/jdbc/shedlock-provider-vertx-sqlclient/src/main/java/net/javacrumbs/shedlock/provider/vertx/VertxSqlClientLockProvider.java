package net.javacrumbs.shedlock.provider.vertx;

import static java.util.Objects.requireNonNull;

import io.vertx.sqlclient.SqlClient;
import java.util.TimeZone;
import net.javacrumbs.shedlock.provider.sql.DatabaseProduct;
import net.javacrumbs.shedlock.provider.sql.SqlConfiguration;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;

/**
 * Lock provider using Vert.x SQL Client (io.vertx.sqlclient.Pool).
 *
 * It reuses shedlock-sql-support for SQL generation and parameter handling.
 */
public class VertxSqlClientLockProvider extends StorageBasedLockProvider {
    public VertxSqlClientLockProvider(Configuration configuration) {
        super(new VertxSqlClientStorageAccessor(configuration));
    }

    public static final class Configuration extends SqlConfiguration {
        private final SqlClient sqlClient;

        Configuration(
                SqlClient sqlClient,
                boolean dbUpperCase,
                DatabaseProduct databaseProduct,
                String tableName,
                boolean forceUtcTimeZone,
                ColumnNames columnNames,
                String lockedByValue,
                boolean useDbTime) {
            super(
                    databaseProduct,
                    dbUpperCase,
                    tableName,
                    forceUtcTimeZone ? TimeZone.getTimeZone("UTC") : null,
                    columnNames,
                    lockedByValue,
                    useDbTime);
            this.sqlClient = requireNonNull(sqlClient, "sqlClient can not be null");
        }

        public SqlClient getSqlClient() {
            return sqlClient;
        }

        public static Configuration.Builder builder(SqlClient sqlClient, DatabaseProduct databaseProduct) {
            return new Configuration.Builder(sqlClient).withDatabaseProduct(databaseProduct);
        }

        public static final class Builder extends SqlConfigurationBuilder<Builder> {
            private final SqlClient sqlClient;
            private boolean forceUtcTimeZone;

            public Builder(SqlClient sqlClient) {
                this.sqlClient = sqlClient;
            }

            public Configuration build() {
                return new Configuration(
                        sqlClient,
                        dbUpperCase,
                        databaseProduct,
                        tableName,
                        forceUtcTimeZone,
                        columnNames,
                        lockedByValue,
                        useDbTime);
            }

            /**
             * Enforces UTC times when sending timestamps to the DB.
             */
            public Builder forceUtcTimeZone() {
                this.forceUtcTimeZone = true;
                return this;
            }
        }
    }
}
