package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

class OracleServerTimeStatementsSource extends SqlStatementsSource {
    private final String lockAtMostFor = "SYS_EXTRACT_UTC(SYSTIMESTAMP) + :lockAtMostFor";

    private static final long millisecondsInDay = 24 * 60 * 60 * 1000;

    OracleServerTimeStatementsSource(JdbcTemplateLockProvider.Configuration configuration) {
        super(configuration);
    }

    @Override
    String getInsertStatement() {
        return "INSERT INTO " + tableName() + "(" + name() + ", " + lockUntil() + ", " + lockedAt() + ", " + lockedBy() + ") VALUES(:name, " + lockAtMostFor + ", SYS_EXTRACT_UTC(SYSTIMESTAMP), :lockedBy)";
    }

    @Override
    public String getUpdateStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = " + lockAtMostFor + ", " + lockedAt() + " = SYS_EXTRACT_UTC(SYSTIMESTAMP), " + lockedBy() + " = :lockedBy WHERE " + lockUntil() + " <= SYS_EXTRACT_UTC(SYSTIMESTAMP)";
    }

    @Override
    public String getUnlockStatement() {
        String lockAtLeastFor = lockedAt() + " + :lockAtLeastFor";
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = CASE WHEN " + lockAtLeastFor + " > SYS_EXTRACT_UTC(SYSTIMESTAMP) THEN " + lockAtLeastFor + " ELSE SYS_EXTRACT_UTC(SYSTIMESTAMP) END WHERE " + name() + " = :name AND " + lockedBy() + " = :lockedBy";
    }

    @Override
    public String getExtendStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = " + lockAtMostFor + " WHERE " + name() + " = :name AND " + lockedBy() + " = :lockedBy AND " + lockUntil() + " > SYS_EXTRACT_UTC(SYSTIMESTAMP)";
    }

    @Override
    @NonNull
    Map<String, Object> params(@NonNull LockConfiguration lockConfiguration) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", lockConfiguration.getName());
        params.put("lockedBy", configuration.getLockedByValue());
        params.put("lockAtMostFor", ((double) lockConfiguration.getLockAtMostFor().toMillis()) / millisecondsInDay);
        params.put("lockAtLeastFor", ((double) lockConfiguration.getLockAtLeastFor().toMillis()) / millisecondsInDay);
        return params;
    }
}
