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
package net.javacrumbs.shedlock.provider.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.TimeZone;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.test.support.jdbc.PostgresConfig;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

public class PostgresJdbcLockProviderIntegrationTest extends AbstractJdbcTest {
    private static final PostgresConfig dbConfig = new PostgresConfig();

    public PostgresJdbcLockProviderIntegrationTest() {
        super(dbConfig);
    }

    /*
     * If we have a TIMESTAMP WITHOUT TIMEZONE column in Postgres, the timezone value is ignored from the timestamp string
     * (timestamp is sent as string see the last line of PgPreparedStatement.setTimestamp). withTimeZone allows to set the
     * UTC timezone so it works well.
     */
    @Test
    void shouldHonorTimezone() {
        Instant lockUntil = Instant.parse("2020-04-10T17:30:00Z");
        Instant now = lockUntil.minusSeconds(10);

        DataSource datasource = dbConfig.getDataSource();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(datasource);
        jdbcTemplate.execute(dbConfig.getCreateTableStatement());

        TimeZone utc = TimeZone.getTimeZone("UTC");

        JdbcLockProvider provider = new JdbcLockProvider(JdbcLockProvider.Configuration.builder(datasource)
                .withTimeZone(utc)
                .build());

        TimeZone originalTimezone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            // We have set UTC so the default local timezone is ignored
            // https://github.com/lukas-krecan/ShedLock/issues/91
            provider.lock(new LockConfiguration(now, "timezone_test", Duration.ofSeconds(10), Duration.ZERO));

            TimeZone.setDefault(utc);

            new JdbcTemplate(datasource).query("SELECT * FROM shedlock where name='timezone_test'", rs -> {
                Timestamp timestamp = rs.getTimestamp("lock_until");
                assertThat(timestamp.toInstant()).isEqualTo(lockUntil);
            });
        } finally {
            TimeZone.setDefault(originalTimezone);
        }
    }
}
