/**
 * Copyright 2009-2016 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.jdbc;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.LockException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.time.Instant;

import static java.util.Objects.requireNonNull;

class JdbcStorageAccessor extends AbstractStorageAccessor {
    private final DataSource dataSource;
    private final String tableName;

    JdbcStorageAccessor(DataSource jdbcTemplate, String tableName) {
        this.dataSource = requireNonNull(jdbcTemplate, "dataSource can not be null");
        this.tableName = requireNonNull(tableName, "tableName can not be null");
    }

    @Override
    public boolean insertRecord(LockConfiguration lockConfiguration) {
        // Try to insert if the record does not exists (not optimal, but the simplest platform agnostic way)
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement("INSERT INTO " + tableName + "(name, lock_until, locked_at, locked_by) VALUES(?, ?, ?, ?)")
        ) {
            statement.setString(1, lockConfiguration.getName());
            statement.setTimestamp(2, Timestamp.from(lockConfiguration.getLockUntil()));
            statement.setTimestamp(3, Timestamp.from(Instant.now()));
            statement.setString(4, getHostname());
            int insertedRows = statement.executeUpdate();
            if (insertedRows > 0) {
                return true;
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            // lock record already exists
        } catch (SQLException e) {
            // can not throw exception here, some drivers (Postgres) do not throw SQLIntegrityConstraintViolationException on duplicate key
            // we will try update in the next step, su if there is another problem, an exception will be thrown there
            logger.debug("Exception thrown when inserting record", e);
        }
        return false;
    }

    @Override
    public boolean updateRecord(LockConfiguration lockConfiguration) {
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement("UPDATE " + tableName + " SET lock_until = ?, locked_at = ?, locked_by = ? WHERE name = ? AND lock_until <= ?")
        ) {
            Timestamp now = Timestamp.from(Instant.now());
            statement.setTimestamp(1, Timestamp.from(lockConfiguration.getLockUntil()));
            statement.setTimestamp(2, now);
            statement.setString(3, getHostname());
            statement.setString(4, lockConfiguration.getName());
            statement.setTimestamp(5, now);
            int updatedRows = statement.executeUpdate();
            return updatedRows > 0;
        } catch (SQLException e) {
            throw new LockException("Unexpected exception when locking", e);
        }
    }

    @Override
    public void unlock(LockConfiguration lockConfiguration) {
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement("UPDATE " + tableName + " SET lock_until = ? WHERE name = ?")
        ) {
            statement.setTimestamp(1, Timestamp.from(Instant.now()));
            statement.setString(2, lockConfiguration.getName());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new LockException("Unexpected exception when unlocking", e);
        }
    }
}
