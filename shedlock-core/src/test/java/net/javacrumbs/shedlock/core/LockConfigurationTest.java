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
package net.javacrumbs.shedlock.core;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class LockConfigurationTest {

    @Test
    void lockAtLeastUntilShouldBeBeforeOrEqualsToLockAtMostUntil() {
        Instant time = Instant.now().plusSeconds(5);
        new LockConfiguration("name", time, time);
        new LockConfiguration("name", time.plusMillis(1), time);

        assertThatThrownBy(() -> new LockConfiguration("name", time, time.plusMillis(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lockAtMostUntilHasToBeInTheFuture() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new LockConfiguration("name", now.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nameShouldNotBeEmpty() {
        assertThatThrownBy(() -> new LockConfiguration("", Instant.now().plusSeconds(5))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defaultClockSystemUTC() {
        Instant now = Instant.now();

        LockConfiguration lockConfiguration = new LockConfiguration("name", now.plusSeconds(5));

        assertThat(lockConfiguration.getLockAtLeastUntil()).isAfterOrEqualTo(now);
        assertThat(lockConfiguration.getLockAtMostUntil()).isEqualTo(now.plusSeconds(5));
        assertThat(lockConfiguration.getUnlockTime()).isAfterOrEqualTo(now);
    }

    @Test
    void canPassCustomClock() {
        ZoneId zoneId = ZoneId.of("Europe/Helsinki");
        LocalDateTime fixedTime = LocalDateTime.of(LocalDate.of(2020, 2, 14), LocalTime.MIDNIGHT);
        Clock fixedClock = Clock.fixed(fixedTime.atZone(zoneId).toInstant(), zoneId);

        LockConfiguration lockConfiguration = new LockConfiguration("name", Instant.now(fixedClock).plusSeconds(5), fixedClock);
        assertThat(lockConfiguration.getLockAtLeastUntil()).isEqualTo(fixedClock.instant());
        assertThat(lockConfiguration.getLockAtMostUntil()).isEqualTo(fixedClock.instant().plusSeconds(5));
        assertThat(lockConfiguration.getUnlockTime()).isEqualTo(fixedClock.instant());
    }
}