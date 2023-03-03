/**
 * Copyright 2009 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.jdbctemplate;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void shouldTableAndColumNamesUpperCase() {
        final var config = JdbcTemplateLockProvider
            .Configuration
            .builder()
            .withJdbcTemplate(mock(JdbcTemplate.class))
            .withDbUpperCase(true)
            .build();

        assertThat(config.getTableName()).isUpperCase();
        assertThat(config.getColumnNames().getName()).isUpperCase();
        assertThat(config.getColumnNames().getLockedBy()).isUpperCase();
        assertThat(config.getColumnNames().getLockedAt()).isUpperCase();
        assertThat(config.getColumnNames().getLockUntil()).isUpperCase();
    }

    @Test
    void shouldTableAndColumNamesLowerCaseByDefault() {
        final var config = JdbcTemplateLockProvider
            .Configuration
            .builder()
            .withJdbcTemplate(mock(JdbcTemplate.class))
            .build();

        assertThat(config.getTableName()).isLowerCase();
        assertThat(config.getColumnNames().getName()).isLowerCase();
        assertThat(config.getColumnNames().getLockedBy()).isLowerCase();
        assertThat(config.getColumnNames().getLockedAt()).isLowerCase();
        assertThat(config.getColumnNames().getLockUntil()).isLowerCase();
    }
}
