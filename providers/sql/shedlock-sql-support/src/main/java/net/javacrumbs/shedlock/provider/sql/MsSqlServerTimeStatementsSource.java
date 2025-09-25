package net.javacrumbs.shedlock.provider.sql;

import java.util.Map;
import net.javacrumbs.shedlock.core.LockConfiguration;

class MsSqlServerTimeStatementsSource extends SqlStatementsSource {
    private static final String now = "SYSUTCDATETIME()";
    private static final String lockAtMostFor = "DATEADD(millisecond, :lockAtMostForMillis, " + now + ")";

    MsSqlServerTimeStatementsSource(SqlConfiguration configuration) {
        super(configuration);
    }

    @Override
    public String getInsertStatement() {
        return "INSERT INTO " + tableName() + "(" + name() + ", " + lockUntil() + ", " + lockedAt() + ", " + lockedBy()
                + ") VALUES(:name, " + lockAtMostFor + ", " + now + ", :lockedBy)";
    }

    @Override
    public String getUpdateStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = " + lockAtMostFor + ", " + lockedAt() + " = " + now
                + ", " + lockedBy() + " = :lockedBy WHERE " + name() + " = :name AND " + lockUntil() + " <= " + now;
    }

    @Override
    public String getUnlockStatement() {
        String lockAtLeastFor = "DATEADD(millisecond, :lockAtLeastForMillis, " + lockedAt() + ")";
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = CASE WHEN " + lockAtLeastFor + " > " + now
                + " THEN " + lockAtLeastFor + " ELSE " + now + " END WHERE " + name() + " = :name AND " + lockedBy()
                + " = :lockedBy";
    }

    @Override
    public String getExtendStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = " + lockAtMostFor + " WHERE " + name()
                + " = :name AND " + lockedBy() + " = :lockedBy AND " + lockUntil() + " > " + now;
    }

    @Override
    public Map<String, Object> params(LockConfiguration lockConfiguration) {
        return Map.of(
                "name",
                lockConfiguration.getName(),
                "lockedBy",
                configuration.getLockedByValue(),
                "lockAtMostForMillis",
                lockConfiguration.getLockAtMostFor().toMillis(),
                "lockAtLeastForMillis",
                lockConfiguration.getLockAtLeastFor().toMillis());
    }
}
