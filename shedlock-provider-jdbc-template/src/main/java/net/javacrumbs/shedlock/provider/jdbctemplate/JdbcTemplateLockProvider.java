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
package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.core.support.LockRecordRegistry;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Lock provided by JdbcTemplate. It uses a table that contains lock_name and locked_until.
 * <ol>
 * <li>
 * Attempts to insert a new lock record. Since lock name is a primary key, it fails if the record already exists. As an optimization,
 * we keep in-memory track of created  lock records.
 * </li>
 * <li>
 * If the insert succeeds (1 inserted row) we have the lock.
 * </li>
 * <li>
 * If the insert failed due to duplicate key or we have skipped the insertion, we will try to update lock record using
 * UPDATE tableName SET lock_until = :lockUntil WHERE name = :lockName AND lock_until <= :now
 * </li>
 * <li>
 * If the update succeeded (1 updated row), we have the lock. If the update failed (0 updated rows) somebody else holds the lock
 * </li>
 * <li>
 * When unlocking, lock_until is set to now.
 * </li>
 * </ol>
 */
public class JdbcTemplateLockProvider implements LockProvider {
    private final LockRecordRegistry lockRecordRegistry = new LockRecordRegistry();

    private final NamedParameterJdbcOperations jdbcTemplate;
    private final String tableName;

    public JdbcTemplateLockProvider(DataSource datasource, String tableName) {
        this(new NamedParameterJdbcTemplate(datasource), tableName);
    }

    public JdbcTemplateLockProvider(NamedParameterJdbcOperations jdbcTemplate, String tableName) {
        this.jdbcTemplate = requireNonNull(jdbcTemplate, "jdbcTemplate can not be null");
        this.tableName = requireNonNull(tableName, "tableName can not be null");
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        Map<String, Object> params = createParams(lockConfiguration);

        String name = lockConfiguration.getName();
        if (!lockRecordRegistry.lockRecordRecentlyCreated(name)) {
            try {
                // Try to insert if the record does not exists (not optimal, but the simplest platform agnostic way)
                int insertedRows = jdbcTemplate.update(getInsertStatement(), params);
                lockRecordRegistry.addLockRecord(name);
                if (insertedRows > 0) {
                    return Optional.of(new JdbcLock(lockConfiguration));
                }
            } catch (DuplicateKeyException e) {
                // lock record already exists
                lockRecordRegistry.addLockRecord(name);
            }
        }
        // Row already exists, update it if lock_until <= now()
        int updatedRows = jdbcTemplate.update(getUpdateStatement(), params);
        if (updatedRows > 0) {
            return Optional.of(new JdbcLock(lockConfiguration));
        } else {
            return Optional.empty();
        }
    }

    private class JdbcLock implements SimpleLock {
        private final LockConfiguration lockConfiguration;

        JdbcLock(LockConfiguration lockConfiguration) {
            this.lockConfiguration = lockConfiguration;
        }

        @Override
        public void unlock() {
            Map<String, Object> params = createParams(lockConfiguration);
            jdbcTemplate.update(getUnlockStatement(), params);
        }
    }

    protected Map<String, Object> createParams(LockConfiguration lockConfiguration) {
        Map<String, Object> params = new HashMap<>();
        params.put("lockName", lockConfiguration.getName());
        params.put("lockUntil", Date.from(lockConfiguration.getLockUntil()));
        params.put("now", new Date());
        params.put("lockedBy", getHostname());
        return params;
    }

    protected String getInsertStatement() {
        return "INSERT INTO " + tableName + "(name, lock_until, locked_at, locked_by) VALUES(:lockName, :lockUntil, :now, :lockedBy)";
    }

    protected String getUpdateStatement() {
        return "UPDATE " + tableName + " SET lock_until = :lockUntil, locked_at = :now, locked_by = :lockedBy WHERE name = :lockName AND lock_until <= :now";
    }

    protected String getUnlockStatement() {
        return "UPDATE " + tableName + " SET lock_until = :now WHERE name = :lockName";
    }

    private static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
