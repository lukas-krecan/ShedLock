package net.javacrumbs.shedlock.provider.vertx;

import static java.util.Objects.requireNonNull;

import io.vertx.sqlclient.Pool;
import java.util.TimeZone;
import net.javacrumbs.shedlock.provider.sql.DatabaseProduct;
import net.javacrumbs.shedlock.provider.sql.SqlConfiguration;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import org.jspecify.annotations.Nullable;

/**
 * Lock provider using Vert.x SQL Client (io.vertx.sqlclient.Pool).
 *
 * It reuses shedlock-sql-support for SQL generation and parameter handling.
 */
public class VertxSqlClientLockProvider extends StorageBasedLockProvider {
    public VertxSqlClientLockProvider(Pool pool) {
        this(pool, "shedlock");
    }

    public VertxSqlClientLockProvider(Pool pool, String tableName) {
        this(Configuration.builder(pool).withTableName(tableName).build());
    }

    public VertxSqlClientLockProvider(Configuration configuration) {
        super(new VertxSqlClientStorageAccessor(configuration));
    }

    public static final class Configuration extends SqlConfiguration {
        private final Pool pool;

        Configuration(
                Pool pool,
                boolean dbUpperCase,
                @Nullable DatabaseProduct databaseProduct,
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
            this.pool = requireNonNull(pool, "pool can not be null");
        }

        public Pool getPool() {
            return pool;
        }

        public static Configuration.Builder builder(Pool pool) {
            return new Configuration.Builder(pool);
        }

        public static final class Builder extends SqlConfigurationBuilder<Builder> {
            private final Pool pool;
            private boolean forceUtcTimeZone;

            public Builder(Pool pool) {
                this.pool = pool;
            }

            public Configuration build() {
                // Default to PostgreSQL if not specified, since Vert.x Pool does not expose DB metadata
                // FIXME
                var dbProduct = databaseProduct != null ? databaseProduct : DatabaseProduct.POSTGRES_SQL;
                return new Configuration(
                        pool,
                        dbUpperCase,
                        dbProduct,
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
