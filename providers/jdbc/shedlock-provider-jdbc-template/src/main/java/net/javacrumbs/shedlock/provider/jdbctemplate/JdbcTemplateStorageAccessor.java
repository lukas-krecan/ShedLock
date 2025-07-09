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

import java.util.Map;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider.Configuration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.annotation.Nullable;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

/** Spring JdbcTemplate based implementation usable in JTA environment */
class JdbcTemplateStorageAccessor extends AbstractStorageAccessor {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Configuration configuration;

    @Nullable
    private SqlStatementsSource sqlStatementsSource;

    JdbcTemplateStorageAccessor(Configuration configuration) {
        requireNonNull(configuration, "configuration can not be null");
        this.jdbcTemplate = new NamedParameterJdbcTemplate(configuration.getJdbcTemplate());
        this.configuration = configuration;
        PlatformTransactionManager transactionManager = configuration.getTransactionManager() != null
                ? configuration.getTransactionManager()
                : new DataSourceTransactionManager(
                        configuration.getJdbcTemplate().getDataSource());

        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        if (configuration.getIsolationLevel() != null) {
            this.transactionTemplate.setIsolationLevel(configuration.getIsolationLevel());
        }
    }

    @Override
    public boolean insertRecord(LockConfiguration lockConfiguration) {
        try {
            String sql = sqlStatementsSource().getInsertStatement();
            return execute(sql, lockConfiguration);
        } catch (DuplicateKeyException | ConcurrencyFailureException | TransactionSystemException e) {
            logger.debug("Duplicate key", e);
            return false;
        } catch (DataIntegrityViolationException | BadSqlGrammarException | UncategorizedSQLException e) {
            if (configuration.isThrowUnexpectedException()) {
                throw e;
            }
            logger.error("Unexpected exception", e);
            return false;
        }
    }

    @Override
    public boolean updateRecord(LockConfiguration lockConfiguration) {
        String sql = sqlStatementsSource().getUpdateStatement();
        try {
            return execute(sql, lockConfiguration);
        } catch (ConcurrencyFailureException e) {
            logger.debug("Serialization exception", e);
            return false;
        } catch (DataIntegrityViolationException | TransactionSystemException | UncategorizedSQLException e) {
            if (configuration.isThrowUnexpectedException()) {
                throw e;
            }
            logger.error("Unexpected exception", e);
            return false;
        }
    }

    @Override
    public boolean extend(LockConfiguration lockConfiguration) {
        String sql = sqlStatementsSource().getExtendStatement();

        logger.debug("Extending lock={} until={}", lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());
        return execute(sql, lockConfiguration);
    }

    @Override
    public void unlock(LockConfiguration lockConfiguration) {
        for (int i = 0; i < 10; i++) {
            try {
                doUnlock(lockConfiguration);
                return;
            } catch (ConcurrencyFailureException | TransactionSystemException e) {
                logger.info("Unlock failed due to TransactionSystemException - retrying attempt {}", i + 1);
            }
        }
        logger.error("Unlock failed after 10 attempts");
    }

    private void doUnlock(LockConfiguration lockConfiguration) {
        String sql = sqlStatementsSource().getUnlockStatement();
        execute(sql, lockConfiguration);
    }

    @SuppressWarnings("ConstantConditions")
    private boolean execute(String sql, LockConfiguration lockConfiguration) throws TransactionException {
        return transactionTemplate.execute(status -> jdbcTemplate.update(sql, params(lockConfiguration)) > 0);
    }

    private Map<String, Object> params(LockConfiguration lockConfiguration) {
        return sqlStatementsSource().params(lockConfiguration);
    }

    private SqlStatementsSource sqlStatementsSource() {
        synchronized (configuration) {
            if (sqlStatementsSource == null) {
                sqlStatementsSource = SqlStatementsSource.create(configuration);
            }
            return sqlStatementsSource;
        }
    }
}
