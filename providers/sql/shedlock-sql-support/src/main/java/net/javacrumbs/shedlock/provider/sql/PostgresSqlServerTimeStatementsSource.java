package net.javacrumbs.shedlock.provider.sql;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import net.javacrumbs.shedlock.core.LockConfiguration;

class PostgresSqlServerTimeStatementsSource extends SqlStatementsSource {
    private static final String now = "timezone('utc', CURRENT_TIMESTAMP)";
    private static final String lockAtMostFor = now + " + make_interval(secs => :lockAtMostForInterval)";

    PostgresSqlServerTimeStatementsSource(SqlConfiguration configuration) {
        super(configuration);
    }

    @Override
    public String getInsertStatement() {
        return "INSERT INTO " + tableName() + "(" + name() + ", " + lockUntil() + ", " + lockedAt() + ", " + lockedBy()
                + ") VALUES(:name, " + lockAtMostFor + ", " + now + ", :lockedBy)" + " ON CONFLICT (" + name()
                + ") DO UPDATE" + updateClause();
    }

    private String updateClause() {
        return " SET " + lockUntil() + " = " + lockAtMostFor + ", " + lockedAt() + " = " + now + ", " + lockedBy()
                + " = :lockedBy WHERE " + tableName() + "." + name() + " = :name AND " + tableName() + "." + lockUntil()
                + " <= " + now;
    }

    @Override
    public String getUpdateStatement() {
        return "UPDATE " + tableName() + updateClause();
    }

    @Override
    public String getUnlockStatement() {
        String lockAtLeastFor = lockedAt() + " + make_interval(secs => :lockAtLeastForInterval)";
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
                "lockAtMostForInterval",
                toSeconds(lockConfiguration.getLockAtMostFor()),
                "lockAtLeastForInterval",
                toSeconds(lockConfiguration.getLockAtLeastFor()));
    }

    private static BigDecimal toSeconds(Duration duration) {
        return BigDecimal.valueOf(duration.toMillis()).scaleByPowerOfTen(-3);
    }
}
