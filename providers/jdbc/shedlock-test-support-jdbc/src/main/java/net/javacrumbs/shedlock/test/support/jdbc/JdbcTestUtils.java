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
package net.javacrumbs.shedlock.test.support.jdbc;

import static java.util.Objects.requireNonNull;

import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcTestUtils {

    private final JdbcTemplate jdbcTemplate;
    private final DbConfig dbConfig;

    public JdbcTestUtils(DbConfig dbConfig) {
        jdbcTemplate = new JdbcTemplate(dbConfig.getDataSource());
        jdbcTemplate.execute(dbConfig.getCreateTableStatement());

        this.dbConfig = dbConfig;
    }

    public Timestamp getLockedUntil(String lockName) {
        return requireNonNull(jdbcTemplate.queryForObject(
                "SELECT lock_until FROM shedlock WHERE name = ?", Timestamp.class, lockName));
    }

    public LockInfo getLockInfo(String lockName) {
        return jdbcTemplate
                .query(
                        "SELECT name, lock_until, " + dbConfig.nowExpression()
                                + " as db_time FROM shedlock WHERE name = ?",
                        (rs, rowNum) -> new LockInfo(
                                rs.getString("name"),
                                rs.getTimestamp("lock_until").toInstant(),
                                rs.getTimestamp("db_time").toInstant()),
                        lockName)
                .get(0);
    }

    public void clean() {
        jdbcTemplate.execute("DROP TABLE shedlock");
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }

    public DataSource getDatasource() {
        return dbConfig.getDataSource();
    }

    public static class LockInfo {
        private final String name;
        private final Instant lockUntil;
        private final Instant dbTime;

        public LockInfo(String name, Instant lockUntil, Instant dbTime) {
            this.name = name;
            this.lockUntil = lockUntil;
            this.dbTime = dbTime;
        }

        public String getName() {
            return name;
        }

        public Instant getLockUntil() {
            return lockUntil;
        }

        public Instant getDbTime() {
            return dbTime;
        }

        @Override
        public String toString() {
            return "LockInfo{" + "name='" + name + '\'' + ", lockUntil=" + lockUntil + ", dbTime=" + dbTime + '}';
        }
    }
}
