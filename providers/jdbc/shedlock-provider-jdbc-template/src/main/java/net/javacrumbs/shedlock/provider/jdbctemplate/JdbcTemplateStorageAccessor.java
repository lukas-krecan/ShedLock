/**
 * Copyright 2009-2017 the original author or authors.
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * Spring JdbcTemplate based implementation usable in JTA environment
 */
class JdbcTemplateStorageAccessor extends AbstractStorageAccessor {
	private final String tableName;
	private final JdbcTemplate jdbcTemplate;

	JdbcTemplateStorageAccessor(JdbcTemplate jdbcTemplate, String tableName) {
		this.jdbcTemplate = requireNonNull(jdbcTemplate, "jdbcTemplate can not be null");
		this.tableName = requireNonNull(tableName, "tableName can not be null");
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean insertRecord(LockConfiguration lockConfiguration) {
		String sql = "INSERT INTO " + tableName + "(name, lock_until, locked_at, locked_by) VALUES(?, ?, ?, ?)";
		try
		{
			int insertedRows = jdbcTemplate.update(sql, preparedStatement -> {
				preparedStatement.setString(1, lockConfiguration.getName());
				preparedStatement.setTimestamp(2, Timestamp.from(lockConfiguration.getLockAtMostUntil()));
				preparedStatement.setTimestamp(3, Timestamp.from(Instant.now()));
				preparedStatement.setString(4, getHostname());
			});
			return insertedRows > 0;
		}
		catch (DataIntegrityViolationException e)
		{
			return false;
		}
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean updateRecord(LockConfiguration lockConfiguration) {
		String sql = "UPDATE " + tableName
		             + " SET lock_until = ?, locked_at = ?, locked_by = ? WHERE name = ? AND lock_until <= ?";
		int updatedRows = jdbcTemplate.update(sql, statement -> {
			Timestamp now = Timestamp.from(Instant.now());
			statement.setTimestamp(1, Timestamp.from(lockConfiguration.getLockAtMostUntil()));
			statement.setTimestamp(2, now);
			statement.setString(3, getHostname());
			statement.setString(4, lockConfiguration.getName());
			statement.setTimestamp(5, now);
		});
		return updatedRows > 0;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void unlock(LockConfiguration lockConfiguration) {
		String sql = "UPDATE " + tableName + " SET lock_until = ? WHERE name = ?";
		jdbcTemplate.update(sql, statement -> {
			statement.setTimestamp(1, Timestamp.from(lockConfiguration.getUnlockTime()));
			statement.setString(2, lockConfiguration.getName());
		});
	}

}
