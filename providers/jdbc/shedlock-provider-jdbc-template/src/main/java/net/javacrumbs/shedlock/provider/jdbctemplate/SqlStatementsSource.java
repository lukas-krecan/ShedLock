package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider.Configuration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

class SqlStatementsSource {
    protected final Configuration configuration;

    private static final Logger logger = LoggerFactory.getLogger(SqlStatementsSource.class);

    SqlStatementsSource(Configuration configuration) {
        this.configuration = configuration;
    }

    static SqlStatementsSource create(Configuration configuration) {
        String databaseProductName = getDatabaseProductName(configuration);
        if ("PostgreSQL".equals(databaseProductName)) {
            logger.debug("Using PostgresSqlStatementsSource");
            return new PostgresSqlStatementsSource(configuration);
        } else {
            logger.debug("Using SqlStatementsSource");
            return new SqlStatementsSource(configuration);
        }
    }

    private static String getDatabaseProductName(Configuration configuration) {
        return configuration.getJdbcTemplate().execute((ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
    }

    @NotNull
    Map<String, Object> params(@NotNull LockConfiguration lockConfiguration) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", lockConfiguration.getName());
        params.put("lockUntil", timestamp(lockConfiguration.getLockAtMostUntil()));
        params.put("now", timestamp(ClockProvider.now()));
        params.put("lockedBy", configuration.getLockedByValue());
        params.put("unlockTime", timestamp(lockConfiguration.getUnlockTime()));
        return params;
    }

    @NotNull
    private Object timestamp(Instant time) {
        TimeZone timeZone = configuration.getTimeZone();
        if (timeZone == null) {
            return Timestamp.from(time);
        } else {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(Date.from(time));
            calendar.setTimeZone(timeZone);
            return calendar;
        }
    }


    String getInsertStatement() {
        return "INSERT INTO " + tableName() + "(" + name() + ", " + lockUntil() + ", " + lockedAt() + ", " + lockedBy() + ") VALUES(:name, :lockUntil, :now, :lockedBy)";
    }


    public String getUpdateStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = :lockUntil, " + lockedAt() + " = :now, " + lockedBy() + " = :lockedBy WHERE " + name() + " = :name AND " + lockUntil() + " <= :now";
    }

    public String getExtendStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = :lockUntil WHERE " + name() + " = :name AND " + lockedBy() + " = :lockedBy AND " + lockUntil() + " > :now";
    }

    public String getUnlockStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = :unlockTime WHERE " + name() + " = :name";
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

class PostgresSqlStatementsSource extends SqlStatementsSource {
    private final String lockAtMostFor = "current_timestamp + cast(:lockAtMostForInterval as interval)";

    PostgresSqlStatementsSource(Configuration configuration) {
        super(configuration);
    }

    @Override
    String getInsertStatement() {
        return "INSERT INTO " + tableName() + "(" + name() + ", " + lockUntil() + ", " + lockedAt() + ", " + lockedBy() + ") VALUES(:name, " + lockAtMostFor + ", current_timestamp, :lockedBy)" +
            " ON CONFLICT (" + name() + ") DO UPDATE" + updateClause();
    }

    @NotNull
    private String updateClause() {
        return " SET " + lockUntil() + " = " + lockAtMostFor + ", " + lockedAt() + " = current_timestamp, " + lockedBy() + " = :lockedBy WHERE " + tableName() + "." + lockUntil() + " <= current_timestamp";
    }

    @Override
    public String getUpdateStatement() {
        return "UPDATE " + tableName() + updateClause();
    }

    @Override
    public String getUnlockStatement() {
        String lockAtLeastFor = lockedAt() + " + cast(:lockAtLeastForInterval as interval)";
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = CASE WHEN " + lockAtLeastFor + " > current_timestamp THEN " + lockAtLeastFor + " ELSE current_timestamp END WHERE " + name() + " = :name";
    }

    @Override
    public String getExtendStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = current_timestamp + cast(:lockAtMostForInterval as interval) WHERE " + name() + " = :name AND " + lockedBy() + " = :lockedBy AND " + lockUntil() + " > current_timestamp";
    }

    @Override
    @NotNull Map<String, Object> params(@NotNull LockConfiguration lockConfiguration) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", lockConfiguration.getName());
        params.put("lockedBy", configuration.getLockedByValue());
        params.put("lockAtMostForInterval", lockConfiguration.getLockAtMostFor().toMillis() + " milliseconds");
        params.put("lockAtLeastForInterval", lockConfiguration.getLockAtLeastFor().toMillis() + " milliseconds");
        return params;
    }
}
