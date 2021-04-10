/**
 * Copyright 2009-2021 the original author or authors.
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
package net.javacrumbs.shedlock.provider.micronautdata;

import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionDefinition;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;

import static java.util.Objects.requireNonNull;

class MicronautDataStorageAccessor extends AbstractStorageAccessor {
    private final SynchronousTransactionManager<Connection> transactionManager;
    private final String tableName;

    private final TransactionDefinition.Propagation propagation = TransactionDefinition.Propagation.REQUIRES_NEW;

    MicronautDataStorageAccessor(@NonNull SynchronousTransactionManager<Connection> transactionManager, @NonNull String tableName) {
        this.transactionManager = transactionManager;
        this.tableName = requireNonNull(tableName, "tableName can not be null");
    }

    @Override
    public boolean insertRecord(@NonNull LockConfiguration lockConfiguration) {
        // Try to insert if the record does not exists (not optimal, but the simplest platform agnostic way)
        String sql = "INSERT INTO " + tableName + "(name, lock_until, locked_at, locked_by) VALUES(?, ?, ?, ?)";
            return transactionManager.execute(TransactionDefinition.of(propagation), status -> {
                try (PreparedStatement statement = status.getConnection().prepareStatement(sql)) {
                    statement.setString(1, lockConfiguration.getName());
                    statement.setTimestamp(2, Timestamp.from(lockConfiguration.getLockAtMostUntil()));
                    statement.setTimestamp(3, Timestamp.from(ClockProvider.now()));
                    statement.setString(4, getHostname());
                    return statement.executeUpdate() > 0;
                } catch (SQLException e) {
                    handleInsertionException(sql, e);
                    return false;
                }
            });
    }

    @Override
    public boolean updateRecord(@NonNull LockConfiguration lockConfiguration) {
        String sql = "UPDATE " + tableName + " SET lock_until = ?, locked_at = ?, locked_by = ? WHERE name = ? AND lock_until <= ?";
            return transactionManager.execute(TransactionDefinition.of(propagation), status -> {
                try (PreparedStatement statement = status.getConnection().prepareStatement(sql)) {
                    Timestamp now = Timestamp.from(ClockProvider.now());
                    statement.setTimestamp(1, Timestamp.from(lockConfiguration.getLockAtMostUntil()));
                    statement.setTimestamp(2, now);
                    statement.setString(3, getHostname());
                    statement.setString(4, lockConfiguration.getName());
                    statement.setTimestamp(5, now);
                    return statement.executeUpdate() > 0;
                } catch (SQLException e) {
                    handleInsertionException(sql, e);
                    return false;
                }
            });
    }

    @Override
    public boolean extend(@NonNull LockConfiguration lockConfiguration) {
        String sql = "UPDATE " + tableName + " SET lock_until = ? WHERE name = ? AND locked_by = ? AND lock_until > ? ";

        logger.debug("Extending lock={} until={}", lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());

            return transactionManager.execute(TransactionDefinition.of(propagation), status -> {
                try (PreparedStatement statement = status.getConnection().prepareStatement(sql)) {
                    statement.setTimestamp(1, Timestamp.from(lockConfiguration.getLockAtMostUntil()));
                    statement.setString(2, lockConfiguration.getName());
                    statement.setString(3, getHostname());
                    statement.setTimestamp(4, Timestamp.from(ClockProvider.now()));
                    return statement.executeUpdate() > 0;
                } catch (SQLException e) {
                    handleInsertionException(sql, e);
                    return false;
                }
            });
    }

    @Override
    public void unlock(@NonNull LockConfiguration lockConfiguration) {
        String sql = "UPDATE " + tableName + " SET lock_until = ? WHERE name = ?";
            transactionManager.execute(TransactionDefinition.of(propagation), status -> {
                try (PreparedStatement statement = status.getConnection().prepareStatement(sql)) {
                    statement.setTimestamp(1, Timestamp.from(lockConfiguration.getUnlockTime()));
                    statement.setString(2, lockConfiguration.getName());
                    statement.executeUpdate();
                } catch (SQLException e) {
                    handleInsertionException(sql, e);
                    return false;
                }
                return null;
            });
    }

    void handleInsertionException(String sql, SQLException e) {
        if (e instanceof SQLIntegrityConstraintViolationException) {
            // lock record already exists
        } else {
            // can not throw exception here, some drivers (Postgres) do not throw SQLIntegrityConstraintViolationException on duplicate key
            // we will try update in the next step, su if there is another problem, an exception will be thrown there
            logger.debug("Exception thrown when inserting record", e);
        }
    }

    void handleUpdateException(String sql, SQLException e) {
        throw new LockException("Unexpected exception when locking", e);
    }

    void handleUnlockException(String sql, SQLException e) {
        throw new LockException("Unexpected exception when unlocking", e);
    }
}
