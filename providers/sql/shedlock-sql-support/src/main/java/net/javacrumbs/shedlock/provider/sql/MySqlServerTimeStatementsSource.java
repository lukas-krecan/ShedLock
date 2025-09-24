package net.javacrumbs.shedlock.provider.sql;

import java.util.Map;
import net.javacrumbs.shedlock.core.LockConfiguration;

class MySqlServerTimeStatementsSource extends SqlStatementsSource {
    private static final String now = "UTC_TIMESTAMP(3)";
    private static final String lockAtMostFor = "TIMESTAMPADD(MICROSECOND, :lockAtMostForMicros, " + now + ")";

    MySqlServerTimeStatementsSource(SqlConfiguration configuration) {
        super(configuration);
    }

    @Override
    public String getInsertStatement() {
        return "INSERT IGNORE INTO " + tableName() + "(" + name() + ", " + lockUntil() + ", " + lockedAt() + ", "
                + lockedBy() + ") VALUES(:name, " + lockAtMostFor + ", " + now + ", :lockedBy)";
    }

    @Override
    public String getUpdateStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = " + lockAtMostFor + ", " + lockedAt() + " = " + now
                + ", " + lockedBy() + " = :lockedBy WHERE " + name() + " = :name AND " + lockUntil() + " <= " + now;
    }

    @Override
    public String getUnlockStatement() {
        String lockAtLeastFor = "TIMESTAMPADD(MICROSECOND, :lockAtLeastForMicros, " + lockedAt() + ")";
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = IF (" + lockAtLeastFor + " > " + now + " , "
                + lockAtLeastFor + ", " + now + ") WHERE " + name() + " = :name AND " + lockedBy() + " = :lockedBy";
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
                "lockAtMostForMicros",
                lockConfiguration.getLockAtMostFor().toNanos() / 1_000,
                "lockAtLeastForMicros",
                lockConfiguration.getLockAtLeastFor().toNanos() / 1_000);
    }
}
