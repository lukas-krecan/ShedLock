package net.javacrumbs.shedlock.provider.jdbctemplate;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;


class JdbcTemplateLockProviderTest {
    @Test
    void shouldNotEnableBothTimezoneAndServerTime() {
        assertThatThrownBy(
            () -> JdbcTemplateLockProvider.Configuration.builder()
                .withTimeZone(TimeZone.getTimeZone("Europe/Prague"))
                .withJdbcTemplate(mock(JdbcTemplate.class))
                .usingDbTime()
                .build()
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
