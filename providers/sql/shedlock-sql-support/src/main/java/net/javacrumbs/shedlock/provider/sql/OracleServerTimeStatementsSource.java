package net.javacrumbs.shedlock.provider.sql;

import java.util.Map;
import net.javacrumbs.shedlock.core.LockConfiguration;

class OracleServerTimeStatementsSource extends SqlStatementsSource {
    private static final String now = "SYS_EXTRACT_UTC(SYSTIMESTAMP)";
    private static final String lockAtMostFor = now + " + :lockAtMostFor";

    private static final long millisecondsInDay = 24 * 60 * 60 * 1000;

    OracleServerTimeStatementsSource(SqlConfiguration configuration) {
        super(configuration);
    }

    @Override
    public String getInsertStatement() {
        return "MERGE INTO " + tableName() + " USING (SELECT 1 FROM dual) ON (" + name()
                + " = :name) WHEN MATCHED THEN UPDATE SET " + lockUntil() + " = " + lockAtMostFor + ", " + lockedAt()
                + " = " + now + ", " + lockedBy() + " = :lockedBy WHERE " + name() + " = :name AND " + lockUntil()
                + " <= " + now + " WHEN NOT MATCHED THEN INSERT(" + name() + ", " + lockUntil() + ", " + lockedAt()
                + ", " + lockedBy() + ") VALUES(:name, " + lockAtMostFor + ", " + now + ", :lockedBy)";
    }

    @Override
    public String getUpdateStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = " + lockAtMostFor + ", " + lockedAt() + " = " + now
                + ", " + lockedBy() + " = :lockedBy WHERE " + name() + " = :name AND " + lockUntil() + " <= " + now;
    }

    @Override
    public String getUnlockStatement() {
        String lockAtLeastFor = lockedAt() + " + :lockAtLeastFor";
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
                "lockAtMostFor",
                ((double) lockConfiguration.getLockAtMostFor().toMillis()) / millisecondsInDay,
                "lockAtLeastFor",
                ((double) lockConfiguration.getLockAtLeastFor().toMillis()) / millisecondsInDay);
    }
}
