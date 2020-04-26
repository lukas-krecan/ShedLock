package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

class PostgresSqlServerTimeStatementsSource extends SqlStatementsSource {
    private final String lockAtMostFor = "timezone('utc', CURRENT_TIMESTAMP) + cast(:lockAtMostForInterval as interval)";

    PostgresSqlServerTimeStatementsSource(JdbcTemplateLockProvider.Configuration configuration) {
        super(configuration);
    }

    @Override
    String getInsertStatement() {
        return "INSERT INTO " + tableName() + "(" + name() + ", " + lockUntil() + ", " + lockedAt() + ", " + lockedBy() + ") VALUES(:name, " + lockAtMostFor + ", timezone('utc', CURRENT_TIMESTAMP), :lockedBy)" +
            " ON CONFLICT (" + name() + ") DO UPDATE" + updateClause();
    }

    @NonNull
    private String updateClause() {
        return " SET " + lockUntil() + " = " + lockAtMostFor + ", " + lockedAt() + " = timezone('utc', CURRENT_TIMESTAMP), " + lockedBy() + " = :lockedBy WHERE " + tableName() + "." + lockUntil() + " <= timezone('utc', CURRENT_TIMESTAMP)";
    }

    @Override
    public String getUpdateStatement() {
        return "UPDATE " + tableName() + updateClause();
    }

    @Override
    public String getUnlockStatement() {
        String lockAtLeastFor = lockedAt() + " + cast(:lockAtLeastForInterval as interval)";
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = CASE WHEN " + lockAtLeastFor + " > timezone('utc', CURRENT_TIMESTAMP) THEN " + lockAtLeastFor + " ELSE timezone('utc', CURRENT_TIMESTAMP) END WHERE " + name() + " = :name AND " + lockedBy() + " = :lockedBy";
    }

    @Override
    public String getExtendStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = " + lockAtMostFor +" WHERE " + name() + " = :name AND " + lockedBy() + " = :lockedBy AND " + lockUntil() + " > timezone('utc', CURRENT_TIMESTAMP)";
    }

    @Override
    @NonNull Map<String, Object> params(@NonNull LockConfiguration lockConfiguration) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", lockConfiguration.getName());
        params.put("lockedBy", configuration.getLockedByValue());
        params.put("lockAtMostForInterval", lockConfiguration.getLockAtMostFor().toMillis() + " milliseconds");
        params.put("lockAtLeastForInterval", lockConfiguration.getLockAtLeastFor().toMillis() + " milliseconds");
        return params;
    }
}
