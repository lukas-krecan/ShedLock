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
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractPostgresJdbcLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.TimeZone;

import static net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider.Configuration.builder;
import static org.assertj.core.api.Assertions.assertThat;

public class PostgresJdbcTemplateLockProviderIntegrationTest extends AbstractPostgresJdbcLockProviderIntegrationTest {
    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return new JdbcTemplateLockProvider(getDatasource());
    }

    @AfterEach
    void resetClock() {
        ClockProvider.setClock(Clock.systemDefaultZone());
    }

    @Test
    void shouldHonorTimezone() {
        TimeZone timezone = TimeZone.getTimeZone("America/Los_Angeles");

        Instant lockUntil = Instant.parse("2020-04-10T17:30:00Z");
        ClockProvider.setClock(Clock.fixed(lockUntil.minusSeconds(10), timezone.toZoneId()));

        TimeZone originalTimezone = TimeZone.getDefault();


        DataSource datasource = getDatasource();

        TimeZone.setDefault(timezone);

        try {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(datasource);
            jdbcTemplate.execute("CREATE TABLE shedlock_tz(name VARCHAR(64), lock_until TIMESTAMP WITH TIME ZONE, locked_at TIMESTAMP WITH TIME ZONE, locked_by  VARCHAR(255), PRIMARY KEY (name))");

            JdbcTemplateLockProvider provider = new JdbcTemplateLockProvider(builder()
                .withJdbcTemplate(new JdbcTemplate(datasource))
                .withTableName("shedlock_tz")
                .withTimeZone(timezone)
                .build());


            provider.lock(new LockConfiguration("timezone_test", lockUntil));
            new JdbcTemplate(datasource).query("SELECT * FROM shedlock_tz where name='timezone_test'", rs -> {
                Timestamp timestamp = rs.getTimestamp("lock_until");
                assertThat(timestamp.getTimezoneOffset()).isEqualTo(7 * 60);
                assertThat(timestamp.toInstant()).isEqualTo(lockUntil);
            });
        } finally {
            TimeZone.setDefault(originalTimezone);
        }
    }
}
