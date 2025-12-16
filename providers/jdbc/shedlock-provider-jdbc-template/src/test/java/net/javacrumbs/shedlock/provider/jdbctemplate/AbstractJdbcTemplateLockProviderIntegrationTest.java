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

import static net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider.Configuration.builder;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider.Configuration.Builder;
import net.javacrumbs.shedlock.provider.sql.DatabaseProduct;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractJdbcLockProviderIntegrationTest;
import net.javacrumbs.shedlock.test.support.jdbc.DbConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;

@TestInstance(PER_CLASS)
public abstract class AbstractJdbcTemplateLockProviderIntegrationTest {
    private final DbConfig dbConfig;

    public AbstractJdbcTemplateLockProviderIntegrationTest(DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @BeforeAll
    public void startDb() {
        dbConfig.startDb();
    }

    @AfterAll
    public void shutdownDb() {
        dbConfig.shutdownDb();
    }

    @Nullable
    protected DatabaseProduct getExplicitDatabaseProduct() {
        return null;
    }

    @Nested
    class ClientTime extends AbstractJdbcLockProviderIntegrationTest {
        @Override
        protected DbConfig getDbConfig() {
            return dbConfig;
        }

        @Override
        protected StorageBasedLockProvider getLockProvider() {
            Builder builder = builder()
                    .withJdbcTemplate(new JdbcTemplate(getDatasource()))
                    .withDatabaseProduct(getExplicitDatabaseProduct());
            return new JdbcTemplateLockProvider(builder.build());
        }

        @Override
        protected boolean useDbTime() {
            return false;
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
            Builder builder = builder()
                    .withJdbcTemplate(new JdbcTemplate(getDatasource()))
                    .usingDbTime()
                    .withDatabaseProduct(getExplicitDatabaseProduct());
            return new JdbcTemplateLockProvider(builder.build());
        }

        @Override
        protected boolean useDbTime() {
            return true;
        }
    }

    @Nested
    class StorageAccessor extends AbstractJdbcTemplateStorageAccessorTest {
        StorageAccessor() {
            super(dbConfig, getExplicitDatabaseProduct());
        }
    }
}
