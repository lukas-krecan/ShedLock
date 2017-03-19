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

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

class JdbcTemplateStorageAccessor extends AbstractStorageAccessor {
    private final Logger logger = LoggerFactory.getLogger(JdbcTemplateStorageAccessor.class);

    private final NamedParameterJdbcOperations jdbcTemplate;
    private final String tableName;

    JdbcTemplateStorageAccessor(NamedParameterJdbcOperations jdbcTemplate, String tableName) {
        this.jdbcTemplate = requireNonNull(jdbcTemplate, "jdbcTemplate can not be null");
        this.tableName = requireNonNull(tableName, "tableName can not be null");
    }


    @Override
    public boolean insertRecord(LockConfiguration lockConfiguration) {
        try {
            // Try to insert if the record does not exists (not optimal, but the simplest platform agnostic way)
            Map<String, Object> params = createParams(lockConfiguration);
            logger.trace("Trying to insert lock record {}", params);
            int insertedRows = jdbcTemplate.update(getInsertStatement(), params);
            if (insertedRows > 0) {
                logger.trace("Lock record inserted");
                return true;
            }
        } catch (DataIntegrityViolationException e) {
            // lock record already exists
            // DuplicateKeyException is not enough for Vertica
        }
        logger.trace("Lock record not inserted");
        return false;
    }

    @Override
    public boolean updateRecord(LockConfiguration lockConfiguration) {
        Map<String, Object> params = createParams(lockConfiguration);
        logger.trace("Trying to update lock record {}", params);
        int updatedRows = jdbcTemplate.update(getUpdateStatement(), params);
        boolean updated = updatedRows > 0;
        logger.trace("Lock record updated={}", updated);
        return updated;
    }

    @Override
    public void unlock(LockConfiguration lockConfiguration) {
        Instant now = Instant.now();
        Map<String, Object> params = new HashMap<>();
        params.put("lockName", lockConfiguration.getName());
        params.put("unlockTime", Date.from(lockConfiguration.getUnlockTime()));
        jdbcTemplate.update(getUnlockStatement(), params);
    }

    protected Map<String, Object> createParams(LockConfiguration lockConfiguration) {
        Instant now = Instant.now();
        Map<String, Object> params = new HashMap<>();
        params.put("lockName", lockConfiguration.getName());
        params.put("lockUntil", Timestamp.from(lockConfiguration.getLockAtMostUntil()));
        params.put("now", Timestamp.from(now));
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
        return "UPDATE " + tableName + " SET lock_until = :unlockTime WHERE name = :lockName";
    }
}
