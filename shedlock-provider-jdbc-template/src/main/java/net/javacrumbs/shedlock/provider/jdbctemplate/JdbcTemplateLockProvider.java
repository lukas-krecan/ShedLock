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

import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

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
    public JdbcTemplateLockProvider(DataSource datasource) {
        this(datasource, "shedlock");
    }

    public JdbcTemplateLockProvider(DataSource datasource, String tableName) {
        this(new NamedParameterJdbcTemplate(new WrappingDataSource(datasource)), tableName);
    }

    private JdbcTemplateLockProvider(NamedParameterJdbcOperations jdbcTemplate, String tableName) {
        super(new JdbcTemplateStorageAccessor(jdbcTemplate, tableName));
    }

    /**
     * ShedLock relies on atomic JDBC operations and thus should not participate in a underlying transaction.
     * By using different data source the transaction thread-local does not match and thus the JdbcTemplate
     * does not use transaction.
     */
    private static class WrappingDataSource implements DataSource {
        private final DataSource dataSource;

        private WrappingDataSource(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return dataSource.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return dataSource.getConnection(username, password);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return dataSource.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            dataSource.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            dataSource.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return dataSource.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return dataSource.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return dataSource.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return dataSource.isWrapperFor(iface);
        }
    }
}
