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
package net.javacrumbs.shedlock.core;

import org.junit.jupiter.api.Test;

import static java.time.Duration.ZERO;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofSeconds;
import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class LockConfigurationTest {

    @Test
    void lockAtLeastUntilShouldBeBeforeOrEqualsToLockAtMostUntil() {
        new LockConfiguration(now(), "name", ZERO, ZERO);
        new LockConfiguration(now(),"name", ofMillis(1), ZERO);

        assertThatThrownBy(() -> new LockConfiguration(now(),"name", ZERO, ofMillis(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lockAtMostUntilHasToBeInTheFuture() {
        assertThatThrownBy(() -> new LockConfiguration(now(),"name", ofSeconds(-1), ZERO)).isInstanceOf(IllegalArgumentException.class);
    }


    @Test
    void nameShouldNotBeEmpty() {
        assertThatThrownBy(() -> new LockConfiguration(now(),"", ofSeconds(5), ofSeconds(5))).isInstanceOf(IllegalArgumentException.class);
    }

}
