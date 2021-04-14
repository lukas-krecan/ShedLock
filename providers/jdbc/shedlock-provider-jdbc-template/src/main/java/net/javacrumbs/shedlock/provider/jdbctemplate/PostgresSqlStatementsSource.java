/**
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.jdbctemplate;

class PostgresSqlStatementsSource extends SqlStatementsSource {
    PostgresSqlStatementsSource(JdbcTemplateLockProvider.Configuration configuration) {
        super(configuration);
    }

    @Override
    String getInsertStatement() {
        return super.getInsertStatement() + " ON CONFLICT (" + name() + ") DO UPDATE " +
            "SET " + lockUntil() + " = :lockUntil, " + lockedAt() + " = :now, " + lockedBy() + " = :lockedBy " +
            "WHERE " + tableName() + "." + lockUntil() + " <= :now";
    }

}
