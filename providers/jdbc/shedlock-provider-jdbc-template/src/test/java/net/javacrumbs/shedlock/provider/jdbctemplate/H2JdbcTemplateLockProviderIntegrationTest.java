/**
 * Copyright 2009-2019 the original author or authors.
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

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractH2JdbcLockProviderIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.TimeZone;

import static net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider.Configuration.builder;
import static org.assertj.core.api.Assertions.assertThat;

public class H2JdbcTemplateLockProviderIntegrationTest extends AbstractH2JdbcLockProviderIntegrationTest {
    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return new JdbcTemplateLockProvider(builder()
            .withJdbcTemplate(new JdbcTemplate(getDatasource()))
            .build()
        );
    }

    @Test
    void shouldHonorTimezone() throws SQLException {
        TimeZone timeZone = TimeZone.getTimeZone("America/Los_Angeles");

        TimeZone originalTimezone = TimeZone.getDefault();

        try {
            TimeZone.setDefault(timeZone);

            try (
                Connection conn = getDatasource().getConnection();
                Statement statement = conn.createStatement()
            ) {
                statement.execute("CREATE TABLE shedlock_tz(name VARCHAR(64), lock_until TIMESTAMP WITH TIME ZONE, locked_at TIMESTAMP WITH TIME ZONE, locked_by  VARCHAR(255), PRIMARY KEY (name))");
            }

            JdbcTemplateLockProvider provider = new JdbcTemplateLockProvider(builder()
                .withJdbcTemplate(new JdbcTemplate(getDatasource()))
                .withTableName("shedlock_tz")
                .withTimeZone(timeZone)
                .build());


            Instant lockUntil = Instant.parse("2020-04-10T17:30:00Z");
            provider.lock(new LockConfiguration("timezone_test", lockUntil));
            new JdbcTemplate(getDatasource()).query("SELECT * FROM shedlock_tz where name='timezone_test'", rs -> {
                assertThat(rs.getString("lock_until")).isEqualTo("2020-04-10 10:30:00-07");
                assertThat(rs.getTimestamp("lock_until").toInstant()).isEqualTo(lockUntil);
            });
        } finally {
            TimeZone.setDefault(originalTimezone);
        }
    }
}
