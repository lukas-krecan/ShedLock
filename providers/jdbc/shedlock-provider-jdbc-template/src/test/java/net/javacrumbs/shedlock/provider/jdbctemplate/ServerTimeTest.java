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
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.JdbcTestUtils;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static org.assertj.core.api.Assertions.assertThat;

public interface ServerTimeTest {

    String LOCK_NAME = "new_lock";

    StorageBasedLockProvider getLockProvider();

    JdbcTestUtils getTestUtils();

    @Test
    default void lockUntilShouldBeInUtc() {
        Instant time = Instant.now();
        Optional<SimpleLock> lock = getLockProvider().lock(new LockConfiguration(now(), LOCK_NAME, Duration.ofSeconds(60), Duration.ZERO));
        assertThat(getTestUtils().getLockedUntil(LOCK_NAME).toLocalDateTime()).isBetween(atUtc(time.plusSeconds(50)), atUtc(time.plusSeconds(70)));

        time = Instant.now();
        lock.get().unlock();
        assertThat(getTestUtils().getLockedUntil(LOCK_NAME).toLocalDateTime()).isBetween(atUtc(time.minusSeconds(10)), atUtc(time.plusSeconds(10)));

        time = Instant.now();
        getLockProvider().lock(new LockConfiguration(now(), LOCK_NAME, Duration.ofSeconds(120), Duration.ZERO));
        assertThat(getTestUtils().getLockedUntil(LOCK_NAME).toLocalDateTime()).isBetween(atUtc(time.plusSeconds(110)), atUtc(time.plusSeconds(130)));
    }

    static LocalDateTime atUtc(Instant before) {
        return before.atZone(ZoneId.of("UTC")).toLocalDateTime();
    }
}
