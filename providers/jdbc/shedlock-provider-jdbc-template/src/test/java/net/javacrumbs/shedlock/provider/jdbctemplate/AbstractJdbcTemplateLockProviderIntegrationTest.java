/**
 * Copyright 2009-2020 the original author or authors.
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

import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractJdbcLockProviderIntegrationTest;
import net.javacrumbs.shedlock.test.support.jdbc.DbConfig;
import org.junit.jupiter.api.Nested;
import org.springframework.jdbc.core.JdbcTemplate;

import static net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider.Configuration.builder;

public abstract class AbstractJdbcTemplateLockProviderIntegrationTest {
    private final DbConfig dbConfig;

    public AbstractJdbcTemplateLockProviderIntegrationTest(DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @Nested
    class ClientTime extends ClientTimeJdbcLockProviderIntegrationTest {
        ClientTime() {
            super(dbConfig);
        }
    }

    @Nested
    class DbTime extends AbstractJdbcLockProviderIntegrationTest {
        @Override
        protected DbConfig getDbConfig() {
            return dbConfig;
        }

        @Override
        protected StorageBasedLockProvider getLockProvider() {
            return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration
                .builder()
                .withJdbcTemplate(new JdbcTemplate(getDatasource()))
                .usingDbTime()
                .build()
            );
        }

        @Override
        protected boolean useDbTime() {
            return true;
        }
    }

    @Nested
    class StorageAccessor extends AbstractJdbcTemplateStorageAccessorTest {
        StorageAccessor() {
            super(dbConfig);
        }
    }
}

class ClientTimeJdbcLockProviderIntegrationTest extends AbstractJdbcLockProviderIntegrationTest {
    private final DbConfig dbConfig;

    public ClientTimeJdbcLockProviderIntegrationTest(DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @Override
    protected DbConfig getDbConfig() {
        return dbConfig;
    }

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return new JdbcTemplateLockProvider(builder()
            .withJdbcTemplate(new JdbcTemplate(getDatasource()))
            .build()
        );
    }

    @Override
    protected boolean useDbTime() {
        return false;
    }
}

class DbTimeJdbcLockProviderIntegrationTest extends AbstractJdbcLockProviderIntegrationTest {
    private final DbConfig dbConfig;

    public DbTimeJdbcLockProviderIntegrationTest(DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @Override
    protected DbConfig getDbConfig() {
        return dbConfig;
    }

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration
            .builder()
            .withJdbcTemplate(new JdbcTemplate(getDatasource()))
            .usingDbTime()
            .build()
        );
    }

    @Override
    protected boolean useDbTime() {
        return true;
    }
}

