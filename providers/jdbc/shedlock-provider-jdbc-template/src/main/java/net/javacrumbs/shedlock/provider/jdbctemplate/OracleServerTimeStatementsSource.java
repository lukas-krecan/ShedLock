/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.jdbctemplate;

import java.util.Map;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.annotation.NonNull;

class OracleServerTimeStatementsSource extends SqlStatementsSource {
    private static final String now = "SYS_EXTRACT_UTC(SYSTIMESTAMP)";
    private static final String lockAtMostFor = now + " + :lockAtMostFor";

    private static final long millisecondsInDay = 24 * 60 * 60 * 1000;

    OracleServerTimeStatementsSource(JdbcTemplateLockProvider.Configuration configuration) {
        super(configuration);
    }

    @Override
    String getInsertStatement() {
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
    @NonNull
    Map<String, Object> params(@NonNull LockConfiguration lockConfiguration) {
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
