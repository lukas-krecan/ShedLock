/**
 * Copyright 2009-2019 the original author or authors.
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

import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider.Configuration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import static java.util.Objects.requireNonNull;

/**
 * Spring JdbcTemplate based implementation usable in JTA environment
 */
class JdbcTemplateStorageAccessor extends AbstractStorageAccessor {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Configuration configuration;
    private final SqlStatementsSource sqlStatementsSource;

    JdbcTemplateStorageAccessor(@NotNull Configuration configuration) {
        this.configuration = requireNonNull(configuration, "configuration can not be null");
        this.jdbcTemplate = new NamedParameterJdbcTemplate(configuration.getJdbcTemplate());
        this.sqlStatementsSource = SqlStatementsSource.create(configuration);
        PlatformTransactionManager transactionManager = configuration.getTransactionManager() != null ?
            configuration.getTransactionManager() :
            new DataSourceTransactionManager(configuration.getJdbcTemplate().getDataSource());

        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public boolean insertRecord(@NotNull LockConfiguration lockConfiguration) {
        try {
            String sql = sqlStatementsSource.getInsertStatement();
            return transactionTemplate.execute(status -> {
                Map<String, Object> params = params(lockConfiguration);
                int insertedRows = jdbcTemplate.update(sql, params);
                return insertedRows > 0;
            });
        } catch (DuplicateKeyException e) {
            return false;
        } catch (DataIntegrityViolationException e) {
            logger.warn("Unexpected exception", e);
            return false;
        }
    }

    @Override
    public boolean updateRecord(@NotNull LockConfiguration lockConfiguration) {
        String sql = sqlStatementsSource.getUpdateStatement();
        return transactionTemplate.execute(status -> {
            int updatedRows = jdbcTemplate.update(sql, params(lockConfiguration));
            return updatedRows > 0;
        });
    }

    @Override
    public boolean extend(@NotNull LockConfiguration lockConfiguration) {
        String sql = sqlStatementsSource.getExtendStatement();

        logger.debug("Extending lock={} until={}", lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());
        return transactionTemplate.execute(status -> {
            int updatedRows = jdbcTemplate.update(sql, params(lockConfiguration));
            return updatedRows > 0;
        });
    }

    @Override
    public void unlock(@NotNull LockConfiguration lockConfiguration) {
        String sql = sqlStatementsSource.getUnlockStatement();
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                jdbcTemplate.update(sql, params(lockConfiguration));
            }
        });
    }

    @NotNull
    private Map<String, Object> params(@NotNull LockConfiguration lockConfiguration) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", lockConfiguration.getName());
        params.put("lockUntil", timestamp(lockConfiguration.getLockAtMostUntil()));
        params.put("now", timestamp(ClockProvider.now()));
        params.put("lockedBy", lockedByValue());
        params.put("unlockTime", timestamp(lockConfiguration.getUnlockTime()));
        return params;
    }

    @NotNull
    private Object timestamp(Instant time) {
        TimeZone timeZone = configuration.getTimeZone();
        if (timeZone == null) {
            return Timestamp.from(time);
        } else {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(Date.from(time));
            calendar.setTimeZone(timeZone);
            return calendar;
        }
    }

    private String lockedByValue() {
        return configuration.getLockedByValue();
    }

}
