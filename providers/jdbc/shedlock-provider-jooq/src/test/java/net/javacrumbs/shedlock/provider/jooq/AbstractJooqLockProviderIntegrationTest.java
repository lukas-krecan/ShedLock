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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.NewTransactionRunner;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractJdbcLockProviderIntegrationTest;
import net.javacrumbs.shedlock.test.support.jdbc.DbConfig;
import org.jetbrains.annotations.NotNull;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(PER_CLASS)
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

    @Test
    public void shouldUseNewTransactionRunner() {
        AtomicInteger transactionCount = new AtomicInteger();
        NewTransactionRunner transactionRunner = callback -> {
            transactionCount.incrementAndGet();
            return callback.execute();
        };

        Optional<SimpleLock> lock = new JooqLockProvider(dslContext, transactionRunner).lock(lockConfig(LOCK_NAME1));

        assertThat(lock).isNotEmpty();
        lock.get().unlock();
        assertThat(transactionCount).hasValue(2);
    }

    @Test
    public void shouldUseJooqTransactionRunner() {
        AtomicInteger transactionCount = new AtomicInteger();
        JooqTransactionRunner transactionRunner = (context, callback) -> {
            transactionCount.incrementAndGet();
            return context.transactionResult(callback);
        };

        Optional<SimpleLock> lock = new JooqLockProvider(dslContext, transactionRunner).lock(lockConfig(LOCK_NAME1));

        assertThat(lock).isNotEmpty();
        lock.get().unlock();
        assertThat(transactionCount).hasValue(2);
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
