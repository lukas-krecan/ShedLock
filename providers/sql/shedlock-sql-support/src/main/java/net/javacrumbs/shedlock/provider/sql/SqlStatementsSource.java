package net.javacrumbs.shedlock.provider.sql;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SqlStatementsSource {
    protected final SqlConfiguration configuration;

    private static final Logger logger = LoggerFactory.getLogger(SqlStatementsSource.class);

    SqlStatementsSource(SqlConfiguration configuration) {
        this.configuration = configuration;
    }

    public static SqlStatementsSource create(SqlConfiguration configuration) {
        DatabaseProduct databaseProduct = configuration.getDatabaseProduct();

        if (configuration.getUseDbTime()) {
            if (databaseProduct == null) {
                throw new IllegalStateException("DatabaseProduct must be set when using DB time");
            }
            var statementsSource = createDbTimeStatementSource(databaseProduct, configuration);
            logger.debug("Using {}", statementsSource.getClass().getSimpleName());
            return statementsSource;
        } else {
            if (Objects.equals(databaseProduct, DatabaseProduct.POSTGRES_SQL)) {
                logger.debug("Using PostgresSqlStatementsSource");
                return new PostgresSqlStatementsSource(configuration);
            } else {
                logger.debug("Using SqlStatementsSource");
                return new SqlStatementsSource(configuration);
            }
        }
    }

    /**
     * Create a DB-time capable SqlStatementsSource for the given database product.
     */
    private static SqlStatementsSource createDbTimeStatementSource(
            DatabaseProduct databaseProduct, SqlConfiguration configuration) {
        return switch (databaseProduct) {
            case POSTGRES_SQL -> new PostgresSqlServerTimeStatementsSource(configuration);
            case SQL_SERVER -> new MsSqlServerTimeStatementsSource(configuration);
            case ORACLE -> new OracleServerTimeStatementsSource(configuration);
            case MY_SQL, MARIA_DB -> new MySqlServerTimeStatementsSource(configuration);
            case HQL -> new HsqlServerTimeStatementsSource(configuration);
            case H2 -> new H2ServerTimeStatementsSource(configuration);
            case DB2 -> new Db2ServerTimeStatementsSource(configuration);
            default ->
                throw new UnsupportedOperationException(
                        "DB time is not supported for database product: " + databaseProduct);
        };
    }

    public Map<String, Object> params(LockConfiguration lockConfiguration) {
        return Map.of(
                "name",
                lockConfiguration.getName(),
                "lockUntil",
                timestamp(lockConfiguration.getLockAtMostUntil()),
                "now",
                timestamp(ClockProvider.now()),
                "lockedBy",
                configuration.getLockedByValue(),
                "unlockTime",
                timestamp(lockConfiguration.getUnlockTime()));
    }

    private Object timestamp(Instant time) {
        TimeZone timeZone = configuration.getTimeZone();
        if (timeZone != null) {
            return time.atZone(timeZone.toZoneId());
        } else {
            return time.atZone(ZoneId.systemDefault());
        }
    }

    public String getInsertStatement() {
        return "INSERT INTO " + tableName() + "(" + name() + ", " + lockUntil() + ", " + lockedAt() + ", " + lockedBy()
                + ") VALUES(:name, :lockUntil, :now, :lockedBy)";
    }

    public String getUpdateStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = :lockUntil, " + lockedAt() + " = :now, "
                + lockedBy() + " = :lockedBy WHERE " + name() + " = :name AND " + lockUntil() + " <= :now";
    }

    public String getExtendStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = :lockUntil WHERE " + name() + " = :name AND "
                + lockedBy() + " = :lockedBy AND " + lockUntil() + " > :now";
    }

    public String getUnlockStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = :unlockTime WHERE " + name() + " = :name" + " AND "
                + lockedBy() + " = :lockedBy";
    }

    String name() {
        return configuration.getColumnNames().getName();
    }

    String lockUntil() {
        return configuration.getColumnNames().getLockUntil();
    }

    String lockedAt() {
        return configuration.getColumnNames().getLockedAt();
    }

    String lockedBy() {
        return configuration.getColumnNames().getLockedBy();
    }

    String tableName() {
        return configuration.getTableName();
    }
}
