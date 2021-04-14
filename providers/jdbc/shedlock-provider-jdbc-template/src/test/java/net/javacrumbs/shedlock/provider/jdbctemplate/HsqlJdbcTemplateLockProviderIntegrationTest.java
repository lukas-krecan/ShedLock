/**
 * Copyright 2009 the original author or authors.
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
import net.javacrumbs.shedlock.test.support.jdbc.HsqlConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Optional;

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider.Configuration.builder;

public class HsqlJdbcTemplateLockProviderIntegrationTest extends AbstractJdbcTemplateLockProviderIntegrationTest {
    private static final HsqlConfig dbConfig = new HsqlConfig();

    public HsqlJdbcTemplateLockProviderIntegrationTest() {
        super(dbConfig);
    }

    @BeforeAll
    public static void startDb() {
        dbConfig.startDb();
    }

    @AfterAll
    public static void shutdownDb() {
        dbConfig.shutdownDb();
    }

    @Test
    public void shouldBeAbleToSetCustomColumnNames() throws SQLException {
        try (
            Connection conn = dbConfig.getDataSource().getConnection();
            Statement statement = conn.createStatement()
        ) {
            statement.execute("CREATE TABLE shdlck(n VARCHAR(64), lck_untl TIMESTAMP(3), lckd_at TIMESTAMP(3), lckd_by  VARCHAR(255), PRIMARY KEY (n))");
        }

        JdbcTemplateLockProvider provider = new JdbcTemplateLockProvider(builder()
            .withTableName("shdlck")
            .withColumnNames(new ColumnNames("n", "lck_untl", "lckd_at", "lckd_by"))
            .withJdbcTemplate(new JdbcTemplate(dbConfig.getDataSource()))
            .withLockedByValue("my-value")
            .build());

        Optional<SimpleLock> lock = provider.lock(new LockConfiguration(now(), "test", Duration.ofSeconds(10), Duration.ZERO));
        lock.get().unlock();
    }
}
