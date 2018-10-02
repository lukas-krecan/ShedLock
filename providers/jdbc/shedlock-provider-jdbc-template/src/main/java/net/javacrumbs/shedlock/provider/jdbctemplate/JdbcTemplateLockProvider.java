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
package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

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
 * UPDATE tableName SET lock_until = :lockUntil WHERE name = :lockName AND lock_until &lt;= :now
 * </li>
 * <li>
 * If the update succeeded (1 updated row), we have the lock. If the update failed (0 updated rows) somebody else holds the lock
 * </li>
 * <li>
 * When unlocking, lock_until is set to now.
 * </li>
 * </ol>
 */
public class JdbcTemplateLockProvider extends StorageBasedLockProvider {
    public JdbcTemplateLockProvider(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, (PlatformTransactionManager) null);
    }

    public JdbcTemplateLockProvider(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this(jdbcTemplate, transactionManager, "shedlock");
    }

    public JdbcTemplateLockProvider(JdbcTemplate jdbcTemplate, String tableName) {
        this(jdbcTemplate, null, tableName);
    }

    public JdbcTemplateLockProvider(DataSource dataSource) {
        this(new JdbcTemplate(dataSource));
    }

    public JdbcTemplateLockProvider(DataSource dataSource, String tableName) {
        this(new JdbcTemplate(dataSource), tableName);
    }

    public JdbcTemplateLockProvider(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager, String tableName) {
        super(new JdbcTemplateStorageAccessor(jdbcTemplate, transactionManager, tableName));
    }

}
