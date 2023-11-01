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

class PostgresSqlServerTimeStatementsSource extends SqlStatementsSource {
    private static final String now = "timezone('utc', CURRENT_TIMESTAMP)";
    private static final String lockAtMostFor = now + " + cast(:lockAtMostForInterval as interval)";

    PostgresSqlServerTimeStatementsSource(JdbcTemplateLockProvider.Configuration configuration) {
        super(configuration);
    }

    @Override
    String getInsertStatement() {
        return "INSERT INTO " + tableName() + "(" + name() + ", " + lockUntil() + ", " + lockedAt() + ", " + lockedBy()
                + ") VALUES(:name, " + lockAtMostFor + ", " + now + ", :lockedBy)" + " ON CONFLICT (" + name()
                + ") DO UPDATE" + updateClause();
    }

    @NonNull
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
        String lockAtLeastFor = lockedAt() + " + cast(:lockAtLeastForInterval as interval)";
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
                "lockAtMostForInterval",
                lockConfiguration.getLockAtMostFor().toMillis() + " milliseconds",
                "lockAtLeastForInterval",
                lockConfiguration.getLockAtLeastFor().toMillis() + " milliseconds");
    }
}
