/**
 * Copyright 2009 the original author or authors.
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

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider.Configuration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.annotation.NonNull;
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

import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Spring JdbcTemplate based implementation usable in JTA environment
 */
class JdbcTemplateStorageAccessor extends AbstractStorageAccessor {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Configuration configuration;
    private SqlStatementsSource sqlStatementsSource;

    JdbcTemplateStorageAccessor(@NonNull Configuration configuration) {
        requireNonNull(configuration, "configuration can not be null");
        this.jdbcTemplate = new NamedParameterJdbcTemplate(configuration.getJdbcTemplate());
        this.configuration = configuration;
        PlatformTransactionManager transactionManager = configuration.getTransactionManager() != null ?
            configuration.getTransactionManager() :
            new DataSourceTransactionManager(configuration.getJdbcTemplate().getDataSource());

        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        if (configuration.getIsolationLevel() != null) {
            this.transactionTemplate.setIsolationLevel(configuration.getIsolationLevel());
        }
    }

    @Override
    public boolean insertRecord(@NonNull LockConfiguration lockConfiguration) {
        try {
            String sql = sqlStatementsSource().getInsertStatement();
            return execute(sql, lockConfiguration);
        } catch (DuplicateKeyException | ConcurrencyFailureException e) {
            return false;
        } catch (DataIntegrityViolationException | BadSqlGrammarException | UncategorizedSQLException | TransactionSystemException e) {
            logger.error("Unexpected exception", e);
            return false;
        }
    }

    @Override
    public boolean updateRecord(@NonNull LockConfiguration lockConfiguration) {
        String sql = sqlStatementsSource().getUpdateStatement();
        try {
            return execute(sql, lockConfiguration);
        } catch (ConcurrencyFailureException e) {
            return false;
        } catch (DataIntegrityViolationException | TransactionSystemException e) {
            logger.error("Unexpected exception", e);
            return false;
        }
    }

    @Override
    public boolean extend(@NonNull LockConfiguration lockConfiguration) {
        String sql = sqlStatementsSource().getExtendStatement();

        logger.debug("Extending lock={} until={}", lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());
        return execute(sql, lockConfiguration);
    }

    @Override
    public void unlock(@NonNull LockConfiguration lockConfiguration) {
        try {
            doUnlock(lockConfiguration);
        } catch (TransactionSystemException e) {
            logger.info("Unlock failed due to TransactionSystemException - retrying");
            doUnlock(lockConfiguration);
        }
    }

    private void doUnlock(LockConfiguration lockConfiguration) {
        String sql = sqlStatementsSource().getUnlockStatement();
        execute(sql, lockConfiguration);
    }

    @SuppressWarnings("ConstantConditions")
    private boolean execute(String sql, LockConfiguration lockConfiguration) throws TransactionException {
        return transactionTemplate.execute(status -> jdbcTemplate.update(sql, params(lockConfiguration)) > 0);
    }

    @NonNull
    private Map<String, Object> params(@NonNull LockConfiguration lockConfiguration) {
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
