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
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.requireNonNull;

class JdbcTemplateStorageAccessor extends AbstractStorageAccessor {
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
            int insertedRows = jdbcTemplate.update(getInsertStatement(), createParams(lockConfiguration));
            if (insertedRows > 0) {
                return true;
            }
        } catch (DuplicateKeyException e) {
            // lock record already exists
        }
        return false;
    }

    @Override
    public boolean updateRecord(LockConfiguration lockConfiguration) {
        int updatedRows = jdbcTemplate.update(getUpdateStatement(), createParams(lockConfiguration));
        return updatedRows > 0;
    }

    @Override
    public void unlock(LockConfiguration lockConfiguration) {
        Map<String, Object> params = createParams(lockConfiguration);
        jdbcTemplate.update(getUnlockStatement(), params);
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
}
