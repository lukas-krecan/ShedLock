/**
 * Copyright 2009-2020 the original author or authors.
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
package net.javacrumbs.shedlock.provider.jdbc;

import net.javacrumbs.shedlock.provider.jdbc.internal.AbstractJdbcStorageAccessor;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

class JdbcStorageAccessor extends AbstractJdbcStorageAccessor {

    JdbcStorageAccessor(@NonNull DataSource dataSource, @NonNull String tableName) {
        super(dataSource, tableName);
    }

    @Override
    protected void handleInsertionException(String sql, SQLException e) {
        if (e instanceof SQLIntegrityConstraintViolationException) {
            // lock record already exists
        } else {
            // can not throw exception here, some drivers (Postgres) do not throw SQLIntegrityConstraintViolationException on duplicate key
            // we will try update in the next step, su if there is another problem, an exception will be thrown there
            logger.debug("Exception thrown when inserting record", e);
        }
    }

    @Override
    protected void handleUpdateException(String sql, SQLException e) {
        throw new LockException("Unexpected exception when locking", e);
    }

    @Override
    protected void handleUnlockException(String sql, SQLException e) {
        throw new LockException("Unexpected exception when unlocking", e);
    }
}
