package net.javacrumbs.shedlock.provider.jdbctemplate;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;

class JdbcTemplateStorageAccessorTest {

    @Test
    void shouldDoLazyInit() {
        DataSource dataSource = mock(DataSource.class);
        new JdbcTemplateStorageAccessor(
            JdbcTemplateLockProvider.Configuration
            .builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .build()
        );

        verifyZeroInteractions(dataSource);
    }

}
