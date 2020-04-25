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
package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractMySqlJdbcLockProviderIntegrationTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class MySqlServerTimeJdbcTemplateLockProviderIntegrationTest extends AbstractMySqlJdbcLockProviderIntegrationTest {
    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration
            .builder()
            .withJdbcTemplate(new JdbcTemplate(getDatasource()))
            .usingDbTime()
            .build()
        );
    }

    @Override
    protected void assertUnlocked(String lockName) {
        assertThat(testUtils.getJdbcTemplate().queryForObject("SELECT count(*) FROM shedlock WHERE name = ? and lock_until <= now()", new Object[]{lockName}, Integer.class)).isEqualTo(1);
    }

    @Override
    protected void assertLocked(String lockName) {
        assertThat(testUtils.getJdbcTemplate().queryForObject("SELECT count(*) FROM shedlock WHERE name = ? and lock_until > now()", new Object[]{lockName}, Integer.class)).isEqualTo(1);
    }
}
