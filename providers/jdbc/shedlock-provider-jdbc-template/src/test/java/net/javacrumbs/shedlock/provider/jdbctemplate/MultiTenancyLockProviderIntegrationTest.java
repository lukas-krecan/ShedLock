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

import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import net.javacrumbs.shedlock.test.support.jdbc.H2Config;
import net.javacrumbs.shedlock.test.support.jdbc.HsqlConfig;
import net.javacrumbs.shedlock.test.support.jdbc.JdbcTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider.Configuration.builder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MultiTenancyLockProviderIntegrationTest {
    public static final String LOCK_NAME = "lock_name";
    public static final LockConfiguration LOCK_CONFIGURATION = new LockConfiguration(now(), LOCK_NAME, Duration.ofSeconds(60), Duration.ZERO);
    private static final JdbcTestUtils h2TestUtils;
    private static final JdbcTestUtils hsqlTestUtils;

    static {
        H2Config h2Config = new H2Config();
        h2Config.startDb();
        h2TestUtils = new JdbcTestUtils(h2Config);

        HsqlConfig hsqlConfig = new HsqlConfig();
        hsqlConfig.startDb();
        hsqlTestUtils = new JdbcTestUtils(hsqlConfig);
    }

    @Test
    void shouldUseDifferDatabaseForEachTennant() {
        LockProvider lockProvider = getLockProvider();

        Optional<SimpleLock> lock1 = lockProvider.lock(LOCK_CONFIGURATION);
        assertThat(lock1).isNotEmpty();
        assertThat(h2TestUtils.getLockInfo(LOCK_NAME).getLockUntil()).isAfter(ClockProvider.now());
        assertThatThrownBy(() -> hsqlTestUtils.getLockedUntil(LOCK_NAME)).isInstanceOf(EmptyResultDataAccessException.class);

        lock1.get().unlock();

        Optional<SimpleLock> lock2 = lockProvider.lock(LOCK_CONFIGURATION);
        assertThat(lock2).isNotEmpty();
        assertThat(hsqlTestUtils.getLockInfo(LOCK_NAME).getLockUntil()).isAfter(ClockProvider.now());
        lock2.get().unlock();
    }

    @AfterAll
    static void cleanUp() {
        h2TestUtils.clean();
        hsqlTestUtils.clean();
    }

    private LockProvider getLockProvider() {
        return new SampleLockProvider(h2TestUtils.getJdbcTemplate(), hsqlTestUtils.getJdbcTemplate());
    }

    private static abstract class MultiTenancyLockProvider implements LockProvider {
        private final ConcurrentHashMap<String, LockProvider> providers = new ConcurrentHashMap<>();

        @Override
        public @NonNull Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
            String tenantName = getTenantName(lockConfiguration);
            return providers.computeIfAbsent(tenantName, this::createLockProvider).lock(lockConfiguration);
        }

        protected abstract LockProvider createLockProvider(String tenantName) ;

        protected abstract String getTenantName(LockConfiguration lockConfiguration);
    }

    private static class SampleLockProvider extends MultiTenancyLockProvider {
        private static final String TENANT_1 = "tenant-1";
        private static final String TENANT_2 = "tenant-2";
        private final JdbcTemplate jdbcTemplate1;
        private final JdbcTemplate jdbcTemplate2;
        private final AtomicInteger callNo = new AtomicInteger();

        private SampleLockProvider(JdbcTemplate jdbcTemplate1, JdbcTemplate jdbcTemplate2) {
            this.jdbcTemplate1 = jdbcTemplate1;
            this.jdbcTemplate2 = jdbcTemplate2;
        }


        @Override
        protected LockProvider createLockProvider(String tenantName) {
            if (TENANT_1.equals(tenantName)) {
                return new JdbcTemplateLockProvider(builder()
                    .withJdbcTemplate(jdbcTemplate1)
                    .build()
                );
            } else {
                return new JdbcTemplateLockProvider(builder()
                    .withJdbcTemplate(jdbcTemplate2)
                    .build()
                );
            }
        }

        @Override
        protected String getTenantName(LockConfiguration lockConfiguration) {
            // round robin
            // In reality this would use ThreadLocal to figure out the tenant.
            if (callNo.getAndIncrement() % 2 == 0) {
                return TENANT_1;
            } else {
                return TENANT_2;
            }
        }
    }
}


