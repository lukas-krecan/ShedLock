package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

class Db2ServerTimeStatementsSource extends SqlStatementsSource {
    private final String now = "(CURRENT TIMESTAMP - CURRENT TIMEZONE)";
    private final String lockAtMostFor = "("+ now + " + :lockAtMostForMicros MICROSECONDS)";

    Db2ServerTimeStatementsSource(JdbcTemplateLockProvider.Configuration configuration) {
        super(configuration);
    }

    @Override
    String getInsertStatement() {
        return "INSERT INTO " + tableName() + "(" + name() + ", " + lockUntil() + ", " + lockedAt() + ", " + lockedBy() + ") VALUES(:name, " + lockAtMostFor + ", " + now + ", :lockedBy)";
    }

    @Override
    public String getUpdateStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = " + lockAtMostFor + ", " + lockedAt() + " = " + now + ", " + lockedBy() + " = :lockedBy WHERE " + name() + " = :name AND " + lockUntil() + " <= " + now;
    }

    @Override
    public String getUnlockStatement() {
        String lockAtLeastFor = "(" + lockedAt() + "+ :lockAtLeastForMicros MICROSECONDS)";
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = CASE WHEN " + lockAtLeastFor + " > " + now + " THEN " + lockAtLeastFor + " ELSE " + now + " END WHERE " + name() + " = :name AND " + lockedBy() + " = :lockedBy";
    }

    @Override
    public String getExtendStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = " + lockAtMostFor + " WHERE " + name() + " = :name AND " + lockedBy() + " = :lockedBy AND " + lockUntil() + " > " + now;
    }

    @Override
    @NonNull
    Map<String, Object> params(@NonNull LockConfiguration lockConfiguration) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", lockConfiguration.getName());
        params.put("lockedBy", configuration.getLockedByValue());
        params.put("lockAtMostForMicros", ((double) lockConfiguration.getLockAtMostFor().toNanos() / 1_000));
        params.put("lockAtLeastForMicros", ((double) lockConfiguration.getLockAtLeastFor().toNanos() / 1_000));
        return params;
    }
}
