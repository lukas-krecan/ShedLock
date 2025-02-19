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
package net.javacrumbs.shedlock.provider.jooq;

import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractJdbcLockProviderIntegrationTest;
import net.javacrumbs.shedlock.test.support.jdbc.DbConfig;
import org.jetbrains.annotations.NotNull;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledOnJre;
import org.junit.jupiter.api.condition.JRE;

@TestInstance(PER_CLASS)
@DisabledOnJre(JRE.JAVA_11)
public abstract class AbstractJooqLockProviderIntegrationTest extends AbstractJdbcLockProviderIntegrationTest {
    private final DbConfig dbConfig;

    private final DSLContext dslContext;

    public AbstractJooqLockProviderIntegrationTest(DbConfig dbConfig, @NotNull DSLContext dslContext) {
        this.dbConfig = dbConfig;
        this.dslContext = dslContext;
    }

    @Override
    protected DbConfig getDbConfig() {
        return dbConfig;
    }

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return new JooqLockProvider(dslContext);
    }

    @Override
    protected boolean useDbTime() {
        return true;
    }

    @BeforeAll
    public void startDb() {
        dbConfig.startDb();
    }

    @AfterAll
    public void shutdownDb() {
        dbConfig.shutdownDb();
    }
}
