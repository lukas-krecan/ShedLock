/**
 * Copyright 2009-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * Spring JdbcTemplate based implementation usable in JTA environment
 */
class JdbcTemplateStorageAccessor extends AbstractStorageAccessor {
    private final String tableName;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionOperations transactionTemplate;

    JdbcTemplateStorageAccessor(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager, String tableName) {
        this.jdbcTemplate = requireNonNull(jdbcTemplate, "jdbcTemplate can not be null");
        this.tableName = requireNonNull(tableName, "tableName can not be null");
        this.transactionTemplate = createTransactionTemplate(transactionManager);
    }

    private static TransactionOperations createTransactionTemplate(PlatformTransactionManager transactionManager) {
        if (transactionManager == null) {
            // no transaction provider specified, let's keep the original behavior
            return new TransactionOperations() {
                @Override
                public <T> T execute(TransactionCallback<T> action) throws TransactionException {
                    return action.doInTransaction(null);
                }
            };
        } else {
            // transaction provider specified, let's use it
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return transactionTemplate;
        }
    }

    @Override
    public boolean insertRecord(LockConfiguration lockConfiguration) {
        String sql = "INSERT INTO " + tableName + "(name, lock_until, locked_at, locked_by) VALUES(?, ?, ?, ?)";
        return transactionTemplate.execute(status -> {
            try {
                int insertedRows = jdbcTemplate.update(sql, preparedStatement -> {
                    preparedStatement.setString(1, lockConfiguration.getName());
                    preparedStatement.setTimestamp(2, Timestamp.from(lockConfiguration.getLockAtMostUntil()));
                    preparedStatement.setTimestamp(3, Timestamp.from(Instant.now()));
                    preparedStatement.setString(4, getHostname());
                });
                return insertedRows > 0;
            } catch (DataIntegrityViolationException e) {
                return false;
            }
        });
    }

    @Override
    public boolean updateRecord(LockConfiguration lockConfiguration) {
        String sql = "UPDATE " + tableName
            + " SET lock_until = ?, locked_at = ?, locked_by = ? WHERE name = ? AND lock_until <= ?";
        return transactionTemplate.execute(status -> {
            int updatedRows = jdbcTemplate.update(sql, statement -> {
                Timestamp now = Timestamp.from(Instant.now());
                statement.setTimestamp(1, Timestamp.from(lockConfiguration.getLockAtMostUntil()));
                statement.setTimestamp(2, now);
                statement.setString(3, getHostname());
                statement.setString(4, lockConfiguration.getName());
                statement.setTimestamp(5, now);
            });
            return updatedRows > 0;
        });
    }

    @Override
    public void unlock(LockConfiguration lockConfiguration) {
        String sql = "UPDATE " + tableName + " SET lock_until = ? WHERE name = ?";
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {

                jdbcTemplate.update(sql, statement -> {
                    statement.setTimestamp(1, Timestamp.from(lockConfiguration.getUnlockTime()));
                    statement.setString(2, lockConfiguration.getName());
                });
            }
        });
    }

}
