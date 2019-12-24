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

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider.ColumnNames;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractHsqlJdbcLockProviderIntegrationTest;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Calendar;
import java.util.Optional;
import java.util.TimeZone;

import static net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider.Configuration.builder;

public class HsqlJdbcTemplateLockProviderIntegrationTest extends AbstractHsqlJdbcLockProviderIntegrationTest {

    private static final TimeZone TIME_ZONE = TimeZone.getTimeZone("CEST");

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return new JdbcTemplateLockProvider(builder()
            .withJdbcTemplate(new JdbcTemplate(getDatasource()))
            .withTimeZone(TIME_ZONE)
            .build()
        );
    }

    @Test
    public void shouldBeAbleToSetCustomColumnNames() throws SQLException {
        try (
            Connection conn = getDatasource().getConnection();
            Statement statement = conn.createStatement()
        ) {
            statement.execute("CREATE TABLE shdlck(n VARCHAR(64), lck_untl TIMESTAMP(3), lckd_at TIMESTAMP(3), lckd_by  VARCHAR(255), PRIMARY KEY (n))");
        }

        JdbcTemplateLockProvider provider = new JdbcTemplateLockProvider(builder()
            .withTableName("shdlck")
            .withColumnNames(new ColumnNames("n", "lck_untl", "lckd_at", "lckd_by"))
            .withJdbcTemplate(new JdbcTemplate(getDatasource()))
            .withLockedByValue("my-value")
            .build());

        Optional<SimpleLock> lock = provider.lock(new LockConfiguration("test", Instant.now().plusSeconds(10)));
        lock.get().unlock();
    }

    @Override
    protected Calendar now() {
        return Calendar.getInstance(TIME_ZONE);
    }
}
