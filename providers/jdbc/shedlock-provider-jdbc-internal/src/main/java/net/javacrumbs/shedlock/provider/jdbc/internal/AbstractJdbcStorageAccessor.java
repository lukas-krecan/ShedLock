/**
 * Copyright 2009-2018 the original author or authors.
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
package net.javacrumbs.shedlock.provider.jdbc.internal;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * WARNING: internal class API might be volatile
 */
public abstract class AbstractJdbcStorageAccessor extends AbstractStorageAccessor {
    private final DataSource dataSource;
    private final String tableName;

    protected AbstractJdbcStorageAccessor(DataSource dataSource, String tableName) {
        this.dataSource = requireNonNull(dataSource, "dataSource can not be null");
        this.tableName = requireNonNull(tableName, "tableName can not be null");
    }

    @Override
    public boolean insertRecord(LockConfiguration lockConfiguration) {
        // Try to insert if the record does not exists (not optimal, but the simplest platform agnostic way)
        String sql = "INSERT INTO " + tableName + "(name, lock_until, locked_at, locked_by) VALUES(?, ?, ?, ?)";
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            connection.setAutoCommit(true); // just to be sure, should be set by default
            statement.setString(1, lockConfiguration.getName());
            statement.setTimestamp(2, Timestamp.from(lockConfiguration.getLockAtMostUntil()));
            statement.setTimestamp(3, Timestamp.from(Instant.now()));
            statement.setString(4, getHostname());
            int insertedRows = statement.executeUpdate();
            if (insertedRows > 0) {
                return true;
            }
        } catch (SQLException e) {
            handleInsertionException(sql, e);
        }
        return false;
    }

    protected abstract void handleInsertionException(String sql, SQLException e);

    @Override
    public boolean updateRecord(LockConfiguration lockConfiguration) {
        String sql = "UPDATE " + tableName + " SET lock_until = ?, locked_at = ?, locked_by = ? WHERE name = ? AND lock_until <= ?";
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            connection.setAutoCommit(true); // just to be sure, should be set by default
            Timestamp now = Timestamp.from(Instant.now());
            statement.setTimestamp(1, Timestamp.from(lockConfiguration.getLockAtMostUntil()));
            statement.setTimestamp(2, now);
            statement.setString(3, getHostname());
            statement.setString(4, lockConfiguration.getName());
            statement.setTimestamp(5, now);
            int updatedRows = statement.executeUpdate();
            return updatedRows > 0;
        } catch (SQLException e) {
            handleUpdateException(sql, e);
            return false;
        }
    }

    protected abstract void handleUpdateException(String sql, SQLException e);

    @Override
    public void unlock(LockConfiguration lockConfiguration) {
        String sql = "UPDATE " + tableName + " SET lock_until = ? WHERE name = ?";
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            connection.setAutoCommit(true); // just to be sure, should be set by default
            statement.setTimestamp(1, Timestamp.from(lockConfiguration.getUnlockTime()));
            statement.setString(2, lockConfiguration.getName());
            statement.executeUpdate();
        } catch (SQLException e) {
            handleUnlockException(sql, e);
        }
    }

    protected abstract void handleUnlockException(String sql, SQLException e);
}
