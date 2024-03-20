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

import static java.time.Duration.ZERO;
import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;

class JdbcTemplateStorageAccessorTest {

    private final DataSource dataSource = mock(DataSource.class);
    private final LockConfiguration lockConfiguration =
            new LockConfiguration(now(), "name", Duration.ofSeconds(1), ZERO);

    @Test
    void shouldDoLazyInit() {

        new JdbcTemplateStorageAccessor(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .build());

        verifyNoInteractions(dataSource);
    }

    @Test
    void shouldCatchUnexpectedException() {
        TransactionSystemException exception = new TransactionSystemException("test");
        JdbcTemplateStorageAccessor jdbcTemplateStorageAccessor = createJdbcTemplateStorageAccessor(exception, false);

        assertThat(jdbcTemplateStorageAccessor.updateRecord(lockConfiguration)).isFalse();
    }

    @Test
    void shouldThrowUnexpectedException() {
        TransactionSystemException exception = new TransactionSystemException("test");
        JdbcTemplateStorageAccessor jdbcTemplateStorageAccessor = createJdbcTemplateStorageAccessor(exception, true);

        assertThatThrownBy(() -> jdbcTemplateStorageAccessor.updateRecord(lockConfiguration))
                .isEqualTo(exception);
    }

    @NotNull
    private JdbcTemplateStorageAccessor createJdbcTemplateStorageAccessor(
            TransactionSystemException exception, boolean throwUnexpectedException) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.getDataSource()).thenReturn(dataSource);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);

        JdbcTemplateStorageAccessor jdbcTemplateStorageAccessor =
                new JdbcTemplateStorageAccessor(JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(jdbcTemplate)
                        .withTransactionManager(txManager)
                        .withThrowUnexpectedException(throwUnexpectedException)
                        .build());

        mockUpdateThrowsException(jdbcTemplate, exception);
        return jdbcTemplateStorageAccessor;
    }

    private static void mockUpdateThrowsException(JdbcTemplate jdbcTemplate, TransactionSystemException ex) {
        when(jdbcTemplate.update(any(PreparedStatementCreator.class))).thenThrow(ex);
    }
}
