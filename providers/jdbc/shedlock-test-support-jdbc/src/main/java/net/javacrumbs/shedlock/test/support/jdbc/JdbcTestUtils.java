/**
 * Copyright 2009-2020 the original author or authors.
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
package net.javacrumbs.shedlock.test.support.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;

public final class JdbcTestUtils {

    private final HikariDataSource datasource;
    private final JdbcTemplate jdbcTemplate;

    public JdbcTestUtils(DbConfig dbConfig) {
        datasource = new HikariDataSource();
        datasource.setJdbcUrl(dbConfig.getJdbcUrl());
        datasource.setUsername(dbConfig.getUsername());
        datasource.setPassword(dbConfig.getPassword());

        jdbcTemplate = new JdbcTemplate(datasource);
        jdbcTemplate.execute(dbConfig.getCreateTableStatement());
    }


    public Instant getLockedUntil(String lockName) {
        return jdbcTemplate.queryForObject("SELECT lock_until FROM shedlock WHERE name = ?", new Object[]{lockName}, Timestamp.class).toInstant();
    }

    public void clean() {
        jdbcTemplate.execute("DROP TABLE shedlock");
        datasource.close();
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public DataSource getDatasource() {
        return datasource;
    }
}
