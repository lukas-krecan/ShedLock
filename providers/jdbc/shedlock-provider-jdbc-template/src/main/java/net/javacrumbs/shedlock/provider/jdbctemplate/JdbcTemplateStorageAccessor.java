/**
 * Copyright 2009-2017 the original author or authors.
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

import net.javacrumbs.shedlock.provider.jdbc.internal.AbstractJdbcStorageAccessor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * Spring JdbcTemplate based implementation which at the end does not use much from JdbcTemplate except
 * exception translator. The reason is that we rely on atomic JDBC operations and thus do not want to
 * participate in transactions. It's not easy to not use transactions with JdbcTemplate and since we were using
 * only exception translation, it makes sense to skip JdbcTemplate completely.
 */
class JdbcTemplateStorageAccessor extends AbstractJdbcStorageAccessor {
    private final SQLExceptionTranslator exceptionTranslator;

    JdbcTemplateStorageAccessor(DataSource dataSource, String tableName) {
        super(dataSource, tableName);
        this.exceptionTranslator = new SQLErrorCodeSQLExceptionTranslator(dataSource);
    }

    @Override
    protected void handleInsertionException(String sql, SQLException e) {
        DataAccessException translatedException = exceptionTranslator.translate("InsertLock", sql, e);
        if (translatedException instanceof DataIntegrityViolationException) {
            // lock record already exists
            // DuplicateKeyException is not enough for Vertica
        } else {
            throw translatedException;
        }
    }

    @Override
    protected void handleUpdateException(String sql, SQLException e) {
        throw exceptionTranslator.translate("UpdateLock", sql, e);
    }

    @Override
    protected void handleUnlockException(String sql, SQLException e) {
        throw exceptionTranslator.translate("Unlock", sql, e);
    }
}
