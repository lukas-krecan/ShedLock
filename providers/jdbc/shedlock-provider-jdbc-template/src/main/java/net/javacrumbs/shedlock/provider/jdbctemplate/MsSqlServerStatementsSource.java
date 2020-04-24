package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

class MsSqlServerStatementsSource extends SqlStatementsSource {
    private final String lockAtMostFor = "DATEADD(millisecond, :lockAtMostForMillis, current_timestamp)";

    MsSqlServerStatementsSource(JdbcTemplateLockProvider.Configuration configuration) {
        super(configuration);
    }

    @Override
    String getInsertStatement() {
        return "INSERT INTO " + tableName() + "(" + name() + ", " + lockUntil() + ", " + lockedAt() + ", " + lockedBy() + ") VALUES(:name, " + lockAtMostFor + ", current_timestamp, :lockedBy)";
    }

    @Override
    public String getUpdateStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = " + lockAtMostFor + ", " + lockedAt() + " = current_timestamp, " + lockedBy() + " = :lockedBy WHERE " + tableName() + "." + lockUntil() + " <= current_timestamp";
    }

    @Override
    public String getUnlockStatement() {
        String lockAtLeastFor = "DATEADD(millisecond, :lockAtLeastForMillis, " + lockedAt() + ")";
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = CASE WHEN " + lockAtLeastFor + " > current_timestamp THEN " + lockAtLeastFor + " ELSE current_timestamp END WHERE " + name() + " = :name AND " + lockedBy() + " = :lockedBy";
    }

    @Override
    public String getExtendStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = " + lockAtMostFor + " WHERE " + name() + " = :name AND " + lockedBy() + " = :lockedBy AND " + lockUntil() + " > current_timestamp";
    }

    @Override
    @NonNull Map<String, Object> params(@NonNull LockConfiguration lockConfiguration) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", lockConfiguration.getName());
        params.put("lockedBy", configuration.getLockedByValue());
        params.put("lockAtMostForMillis", lockConfiguration.getLockAtMostFor().toMillis());
        params.put("lockAtLeastForMillis", lockConfiguration.getLockAtLeastFor().toMillis());
        return params;
    }
}
