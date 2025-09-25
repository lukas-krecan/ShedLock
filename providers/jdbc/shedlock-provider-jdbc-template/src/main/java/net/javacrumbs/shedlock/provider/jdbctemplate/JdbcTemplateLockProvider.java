/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.jdbctemplate;

import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.provider.sql.SqlConfiguration.DEFAULT_TABLE_NAME;

import java.util.TimeZone;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.provider.sql.DatabaseProduct;
import net.javacrumbs.shedlock.provider.sql.SqlConfiguration;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Lock provided by JdbcTemplate. It uses a table that contains lock_name and
 * locked_until.
 *
 * <ol>
 * <li>Attempts to insert a new lock record. Since lock name is a primary key,
 * it fails if the record already exists. As an optimization, we keep in-memory
 * track of created lock records.
 * <li>If the insert succeeds (1 inserted row) we have the lock.
 * <li>If the insert failed due to duplicate key or we have skipped the
 * insertion, we will try to update lock record using UPDATE tableName SET
 * lock_until = :lockUntil WHERE name = :lockName AND lock_until &lt;= :now
 * <li>If the update succeeded (1 updated row), we have the lock. If the update
 * failed (0 updated rows) somebody else holds the lock
 * <li>When unlocking, lock_until is set to now.
 * </ol>
 */
public class JdbcTemplateLockProvider extends StorageBasedLockProvider {

    public JdbcTemplateLockProvider(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, (PlatformTransactionManager) null);
    }

    public JdbcTemplateLockProvider(
            JdbcTemplate jdbcTemplate, @Nullable PlatformTransactionManager transactionManager) {
        this(jdbcTemplate, transactionManager, DEFAULT_TABLE_NAME);
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

    public JdbcTemplateLockProvider(
            JdbcTemplate jdbcTemplate, @Nullable PlatformTransactionManager transactionManager, String tableName) {
        this(Configuration.builder()
                .withJdbcTemplate(jdbcTemplate)
                .withTransactionManager(transactionManager)
                .withTableName(tableName)
                .build());
    }

    public JdbcTemplateLockProvider(Configuration configuration) {
        super(new JdbcTemplateStorageAccessor(configuration));
    }

    public static final class Configuration extends SqlConfiguration {
        private final JdbcTemplate jdbcTemplate;

        @Nullable
        private final PlatformTransactionManager transactionManager;

        private final boolean throwUnexpectedException;

        private final @Nullable Integer isolationLevel;

        private static final Logger logger = LoggerFactory.getLogger(Configuration.class);

        Configuration(
                JdbcTemplate jdbcTemplate,
                @Nullable DatabaseProduct databaseProduct,
                boolean dbUpperCase,
                @Nullable PlatformTransactionManager transactionManager,
                String tableName,
                @Nullable TimeZone timeZone,
                ColumnNames columnNames,
                String lockedByValue,
                boolean useDbTime,
                @Nullable Integer isolationLevel,
                boolean throwUnexpectedException) {

            super(databaseProduct, dbUpperCase, tableName, timeZone, columnNames, lockedByValue, useDbTime);
            this.jdbcTemplate = requireNonNull(jdbcTemplate, "jdbcTemplate can not be null");
            this.transactionManager = transactionManager;
            this.isolationLevel = isolationLevel;
            this.throwUnexpectedException = throwUnexpectedException;
        }

        public JdbcTemplate getJdbcTemplate() {
            return jdbcTemplate;
        }

        @Nullable
        public PlatformTransactionManager getTransactionManager() {
            return transactionManager;
        }

        @Nullable
        public Integer getIsolationLevel() {
            return isolationLevel;
        }

        @Override
        public DatabaseProduct getDatabaseProduct() {
            if (super.getDatabaseProduct() != null) {
                return super.getDatabaseProduct();
            }

            try {
                String jdbcProductName = getJdbcTemplate().execute((ConnectionCallback<String>)
                        connection -> connection.getMetaData().getDatabaseProductName());
                return DatabaseProduct.matchProductName(jdbcProductName);
            } catch (Exception e) {
                logger.debug("Can not determine database product name {}", e.getMessage());
                return DatabaseProduct.UNKNOWN;
            }
        }

        public boolean isThrowUnexpectedException() {
            return throwUnexpectedException;
        }

        public static Configuration.Builder builder() {
            return new Configuration.Builder();
        }

        public static final class Builder extends SqlConfigurationBuilder<Builder> {
            private JdbcTemplate jdbcTemplate;

            @Nullable
            private PlatformTransactionManager transactionManager;

            @Nullable
            private TimeZone timeZone;

            private boolean throwUnexpectedException;

            @Nullable
            private Integer isolationLevel;

            public Builder withJdbcTemplate(JdbcTemplate jdbcTemplate) {
                this.jdbcTemplate = jdbcTemplate;
                return this;
            }

            public Builder withTransactionManager(@Nullable PlatformTransactionManager transactionManager) {
                this.transactionManager = transactionManager;
                return this;
            }

            /**
             * @deprecated use forceUtcTimeZone()
             */
            @Deprecated(forRemoval = true)
            public Builder withTimeZone(TimeZone timeZone) {
                this.timeZone = timeZone;
                return this;
            }

            /**
             * Enforces UTC times. When the useDbTime() is not set, the timestamps are sent to the DB in the JVM default timezone.
             * If your server is not in UTC and you are not using TIMEZONE WITH TIMESTAMP or an equivalent, the TZ information
             * may be lost. For example in Postgres.
             */
            public Builder forceUtcTimeZone() {
                this.timeZone = TimeZone.getTimeZone("UTC");
                return this;
            }

            public Builder withThrowUnexpectedException(boolean throwUnexpectedException) {
                this.throwUnexpectedException = throwUnexpectedException;
                return getThis();
            }

            /**
             * Sets the isolation level for ShedLock. See {@link java.sql.Connection} for
             * constant definitions. for constant definitions
             */
            public Builder withIsolationLevel(int isolationLevel) {
                this.isolationLevel = isolationLevel;
                return getThis();
            }

            public JdbcTemplateLockProvider.Configuration build() {
                return new JdbcTemplateLockProvider.Configuration(
                        jdbcTemplate,
                        databaseProduct,
                        dbUpperCase,
                        transactionManager,
                        tableName,
                        timeZone,
                        columnNames,
                        lockedByValue,
                        useDbTime,
                        isolationLevel,
                        throwUnexpectedException);
            }
        }
    }

    public static final class ColumnNames extends SqlConfiguration.ColumnNames {
        public ColumnNames(String name, String lockUntil, String lockedAt, String lockedBy) {
            super(name, lockUntil, lockedAt, lockedBy);
        }
    }
}
