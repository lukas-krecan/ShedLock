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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.SpringNewTransactionRunner;
import net.javacrumbs.shedlock.test.support.jdbc.DbConfig;
import net.javacrumbs.shedlock.test.support.jdbc.HsqlConfig;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.Transaction;
import org.jooq.TransactionContext;
import org.jooq.TransactionProvider;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

public class HsqlJooqLockProviderIntegrationTest extends AbstractJooqLockProviderIntegrationTest {
    private static final DbConfig dbConfig = new HsqlConfig();
    private static final Settings settings = new Settings().withRenderNameCase(RenderNameCase.UPPER);

    public HsqlJooqLockProviderIntegrationTest() {
        super(dbConfig, DSL.using(dbConfig.getDataSource(), SQLDialect.HSQLDB, settings));
    }

    @Test
    public void shouldRollBackLockInOuterSpringTransactionWithoutNewTransactionRunner() {
        DataSource dataSource = dbConfig.getDataSource();
        var transactionManager = new DataSourceTransactionManager(dataSource);
        var transactionTemplate = new TransactionTemplate(transactionManager);
        var dslContext = springParticipatingDslContext(dataSource, transactionManager);
        var rolledBackLockName = LOCK_NAME1 + "-rolled-back";

        JooqLockProvider jooqLockProvider = new JooqLockProvider(dslContext);
        transactionTemplate.executeWithoutResult(status -> {
            Optional<SimpleLock> lock = jooqLockProvider.lock(lockConfig(rolledBackLockName));
            assertThat(lock).isNotEmpty();
            status.setRollbackOnly();
        });

        Optional<SimpleLock> lockAfterRollback = new JooqLockProvider(dslContext).lock(lockConfig(rolledBackLockName));
        assertThat(lockAfterRollback).isNotEmpty();
        lockAfterRollback.get().unlock();
    }

    @Test
    public void shouldCommitLockInNewTransactionEvenIfOuterTransactionRollsBack() {
        DataSource dataSource = dbConfig.getDataSource();
        var transactionManager = new DataSourceTransactionManager(dataSource);
        var transactionTemplate = new TransactionTemplate(transactionManager);
        var dslContext = springParticipatingDslContext(dataSource, transactionManager);
        var lockProvider = new JooqLockProvider(dslContext, new SpringNewTransactionRunner(transactionManager));

        transactionTemplate.executeWithoutResult(status -> {
            Optional<SimpleLock> lock = lockProvider.lock(lockConfig(LOCK_NAME1));
            assertThat(lock).isNotEmpty();
            status.setRollbackOnly();
        });

        assertLocked(LOCK_NAME1);
    }

    @Test
    public void shouldNotCommitCallerTransactionWhenDataSourceRunnerAcquiresLock() throws SQLException {
        DataSource dataSource = dbConfig.getDataSource();
        createMarkerTable(dataSource);
        String lockName = LOCK_NAME1 + "-raw-connection";

        try (Connection ambientConnection = dataSource.getConnection()) {
            ambientConnection.setAutoCommit(false);
            insertMarker(ambientConnection, 1);

            var dslContext = DSL.using(ambientConnection, SQLDialect.HSQLDB, settings);
            Optional<SimpleLock> lock = new JooqLockProvider(
                            dslContext, JooqTransactionRunner.usingDataSource(dataSource))
                    .lock(lockConfig(lockName));

            assertThat(lock).isNotEmpty();
            ambientConnection.rollback();
            assertThat(markerCount(dataSource)).isZero();
            assertLocked(lockName);
            lock.get().unlock();
        } finally {
            dropMarkerTable(dataSource);
        }
    }

    @Test
    public void shouldNotCommitCallerTransactionWhenDataSourceRunnerDoesNotAcquireLock() throws SQLException {
        DataSource dataSource = dbConfig.getDataSource();
        createMarkerTable(dataSource);
        String lockName = LOCK_NAME1 + "-held";
        Optional<SimpleLock> existingLock =
                new JooqLockProvider(DSL.using(dataSource, SQLDialect.HSQLDB, settings)).lock(lockConfig(lockName));
        assertThat(existingLock).isNotEmpty();

        try (Connection ambientConnection = dataSource.getConnection()) {
            ambientConnection.setAutoCommit(false);
            insertMarker(ambientConnection, 1);

            var dslContext = DSL.using(ambientConnection, SQLDialect.HSQLDB, settings);
            Optional<SimpleLock> lock = new JooqLockProvider(
                            dslContext, JooqTransactionRunner.usingDataSource(dataSource))
                    .lock(lockConfig(lockName));

            assertThat(lock).isEmpty();
            ambientConnection.rollback();
            assertThat(markerCount(dataSource)).isZero();
        } finally {
            existingLock.ifPresent(SimpleLock::unlock);
            dropMarkerTable(dataSource);
        }
    }

    private DSLContext springParticipatingDslContext(
            DataSource dataSource, PlatformTransactionManager transactionManager) {
        var transactionAwareDataSource = new TransactionAwareDataSourceProxy(dataSource);
        return DSL.using(transactionAwareDataSource, SQLDialect.HSQLDB, settings)
                .configuration()
                .derive(new SpringRequiredTransactionProvider(transactionManager))
                .dsl();
    }

    private static final class SpringRequiredTransactionProvider implements TransactionProvider {
        private final PlatformTransactionManager transactionManager;

        private SpringRequiredTransactionProvider(PlatformTransactionManager transactionManager) {
            this.transactionManager = transactionManager;
        }

        @Override
        public void begin(TransactionContext ctx) {
            DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
            definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            ctx.transaction(new SpringTransaction(transactionManager.getTransaction(definition)));
        }

        @Override
        public void commit(TransactionContext ctx) {
            transactionManager.commit(transaction(ctx).status);
        }

        @Override
        public void rollback(TransactionContext ctx) {
            transactionManager.rollback(transaction(ctx).status);
        }

        private SpringTransaction transaction(TransactionContext ctx) {
            return (SpringTransaction) ctx.transaction();
        }
    }

    private record SpringTransaction(TransactionStatus status) implements Transaction {}

    private void createMarkerTable(DataSource dataSource) throws SQLException {
        executeStatement(dataSource, "CREATE TABLE marker(id INT PRIMARY KEY)");
    }

    private void dropMarkerTable(DataSource dataSource) throws SQLException {
        executeStatement(dataSource, "DROP TABLE marker");
    }

    private void executeStatement(DataSource dataSource, String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void insertMarker(Connection connection, int id) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO marker(id) VALUES (" + id + ")");
        }
    }

    private int markerCount(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM marker")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
