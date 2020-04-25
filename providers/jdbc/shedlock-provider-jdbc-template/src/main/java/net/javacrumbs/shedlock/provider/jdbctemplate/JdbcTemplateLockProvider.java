/**
 * Copyright 2009-2020 the original author or authors.
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
import net.javacrumbs.shedlock.support.Utils;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import net.javacrumbs.shedlock.support.annotation.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.TimeZone;

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

    private static final String DEFAULT_TABLE_NAME = "shedlock";

    public JdbcTemplateLockProvider(@NonNull JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, (PlatformTransactionManager) null);
    }

    public JdbcTemplateLockProvider(@NonNull JdbcTemplate jdbcTemplate, @Nullable PlatformTransactionManager transactionManager) {
        this(jdbcTemplate, transactionManager, DEFAULT_TABLE_NAME);
    }

    public JdbcTemplateLockProvider(@NonNull JdbcTemplate jdbcTemplate, @NonNull String tableName) {
        this(jdbcTemplate, null, tableName);
    }

    public JdbcTemplateLockProvider(@NonNull DataSource dataSource) {
        this(new JdbcTemplate(dataSource));
    }

    public JdbcTemplateLockProvider(@NonNull DataSource dataSource, @NonNull String tableName) {
        this(new JdbcTemplate(dataSource), tableName);
    }

    public JdbcTemplateLockProvider(@NonNull JdbcTemplate jdbcTemplate, @Nullable PlatformTransactionManager transactionManager, @NonNull String tableName) {
        this(Configuration.builder()
            .withJdbcTemplate(jdbcTemplate)
            .withTransactionManager(transactionManager)
            .withTableName(tableName)
            .build()
        );
    }

    public JdbcTemplateLockProvider(@NonNull Configuration configuration) {
        super(new JdbcTemplateStorageAccessor(configuration));
    }

    public static final class Configuration {
        private final JdbcTemplate jdbcTemplate;
        private final PlatformTransactionManager transactionManager;
        private final String tableName;
        private final TimeZone timeZone;
        private final ColumnNames columnNames;
        private final String lockedByValue;
        private final boolean useServerTime;

        Configuration(
            @NonNull JdbcTemplate jdbcTemplate,
            @Nullable PlatformTransactionManager transactionManager,
            @NonNull String tableName,
            @Nullable TimeZone timeZone,
            @NonNull ColumnNames columnNames,
            @NonNull String lockedByValue,
            boolean useServerTime
        ) {

            this.jdbcTemplate = requireNonNull(jdbcTemplate, "jdbcTemplate can not be null");
            this.transactionManager = transactionManager;
            this.tableName = requireNonNull(tableName, "tableName can not be null");
            this.timeZone = timeZone;
            this.columnNames = requireNonNull(columnNames, "columnNames can not be null");
            this.lockedByValue = requireNonNull(lockedByValue, "lockedByValue can not be null");
            this.useServerTime = useServerTime;
        }

        public JdbcTemplate getJdbcTemplate() {
            return jdbcTemplate;
        }

        public PlatformTransactionManager getTransactionManager() {
            return transactionManager;
        }

        public String getTableName() {
            return tableName;
        }

        public TimeZone getTimeZone() {
            return timeZone;
        }

        public ColumnNames getColumnNames() {
            return columnNames;
        }

        public String getLockedByValue() {
            return lockedByValue;
        }

        public boolean getUseServerTime() {
            return useServerTime;
        }

        public static Configuration.Builder builder() {
            return new Configuration.Builder();
        }


        public static final class Builder {
            private JdbcTemplate jdbcTemplate;
            private PlatformTransactionManager transactionManager;
            private String tableName = DEFAULT_TABLE_NAME;
            private TimeZone timeZone;
            private String lockedByValue = Utils.getHostname();
            private ColumnNames columnNames = new ColumnNames("name", "lock_until", "locked_at", "locked_by");
            private boolean useServerTime = false;

            public Builder withJdbcTemplate(@NonNull JdbcTemplate jdbcTemplate) {
                this.jdbcTemplate = jdbcTemplate;
                return this;
            }

            public Builder withTransactionManager(PlatformTransactionManager transactionManager) {
                this.transactionManager = transactionManager;
                return this;
            }

            public Builder withTableName(@NonNull String tableName) {
                this.tableName = tableName;
                return this;
            }

            public Builder withTimeZone(TimeZone timeZone) {
                this.timeZone = timeZone;
                return this;
            }

            public Builder withColumnNames(ColumnNames columnNames) {
                this.columnNames = columnNames;
                return this;
            }

            /**
             * Value stored in 'locked_by' column. Please use only for debugging purposes.
             */
            public Builder withLockedByValue(String lockedBy) {
                this.lockedByValue = lockedBy;
                return this;
            }

            public Builder usingServerTime() {
                this.useServerTime = true;
                return this;
            }

            public JdbcTemplateLockProvider.Configuration build() {
                return new JdbcTemplateLockProvider.Configuration(jdbcTemplate, transactionManager, tableName, timeZone, columnNames, lockedByValue, useServerTime);
            }
        }

    }

    public static final class ColumnNames {
        private final String name;
        private final String lockUntil;
        private final String lockedAt;
        private final String lockedBy;

        public ColumnNames(String name, String lockUntil, String lockedAt, String lockedBy) {
            this.name = requireNonNull(name, "'name' column name can not be null");
            this.lockUntil = requireNonNull(lockUntil, "'lockUntil' column name can not be null");
            this.lockedAt = requireNonNull(lockedAt, "'lockedAt' column name can not be null");
            this.lockedBy = requireNonNull(lockedBy, "'lockedBy' column name can not be null");
        }

        public String getName() {
            return name;
        }

        public String getLockUntil() {
            return lockUntil;
        }

        public String getLockedAt() {
            return lockedAt;
        }

        public String getLockedBy() {
            return lockedBy;
        }
    }

}
