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

import static java.lang.Thread.sleep;
import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider.Configuration.builder;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.TimeZone;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.test.support.jdbc.PostgresConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

public class PostgresJdbcTemplateLockProviderIntegrationTest extends AbstractJdbcTemplateLockProviderIntegrationTest {
    private static final PostgresConfig dbConfig = new PostgresConfig();

    protected PostgresJdbcTemplateLockProviderIntegrationTest() {
        super(dbConfig);
    }

    @Nested
    class TimezoneTest {
        @AfterEach
        void resetClock() {
            ClockProvider.setClock(Clock.systemDefaultZone());
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
            JdbcTemplateLockProvider provider = new JdbcTemplateLockProvider(builder()
                    .withJdbcTemplate(new JdbcTemplate(datasource))
                    .withTimeZone(utc)
                    .withIsolationLevel(Connection.TRANSACTION_SERIALIZABLE)
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

    @Nested
    class StorageAccessor extends AbstractJdbcTemplateStorageAccessorTest {
        private static final String MY_LOCK = "my-lock";
        private static final String OTHER_LOCK = "other-lock";

        protected StorageAccessor() {
            super(dbConfig);
        }

        @Test
        void shouldUpdateOnInsertAfterValidityOfPreviousEndedWhenNotUsingDbTime() throws InterruptedException {
            shouldUpdateOnInsertAfterValidityOfPreviousEnded(false);
        }

        @Test
        void shouldUpdateOnInsertAfterValidityOfPreviousEndedWhenUsingDbTime() throws InterruptedException {
            shouldUpdateOnInsertAfterValidityOfPreviousEnded(true);
        }

        private void shouldUpdateOnInsertAfterValidityOfPreviousEnded(boolean usingDbTime) throws InterruptedException {
            JdbcTemplateStorageAccessor accessor = getAccessor(usingDbTime);

            accessor.insertRecord(new LockConfiguration(now(), OTHER_LOCK, Duration.ofSeconds(5), Duration.ZERO));
            Timestamp otherLockValidity = getTestUtils().getLockedUntil(OTHER_LOCK);

            assertThat(accessor.insertRecord(
                            new LockConfiguration(now(), MY_LOCK, Duration.ofMillis(10), Duration.ZERO)))
                    .isEqualTo(true);

            sleep(10);

            assertThat(accessor.insertRecord(
                            new LockConfiguration(now(), MY_LOCK, Duration.ofMillis(10), Duration.ZERO)))
                    .isEqualTo(true);

            // check that the other lock has not been affected by "my-lock" update
            assertThat(getTestUtils().getLockedUntil(OTHER_LOCK)).isEqualTo(otherLockValidity);
        }
    }
}
