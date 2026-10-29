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
package net.javacrumbs.shedlock.provider.mongo;

import static java.time.Duration.ZERO;
import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class DbTimeMongoLockProviderIntegrationTest extends MongoLockProviderIntegrationTest {

    @Override
    protected ExtensibleLockProvider getLockProvider() {
        return new MongoLockProvider(getMongo().getDatabase(DB_NAME), true);
    }

    @AfterEach
    void resetClock() {
        ClockProvider.setClock(Clock.systemUTC());
    }

    @Test
    void shouldNotLockWhenClientClockIsAhead() {
        Optional<SimpleLock> lock = getLockProvider().lock(lockConfig(LOCK_NAME1, ofMinutes(5), ZERO));
        assertThat(lock).isNotEmpty();

        // with client time, the lock would look expired
        skewClientClock(ofHours(1));
        assertThat(getLockProvider().lock(lockConfig(LOCK_NAME1, ofMinutes(5), ZERO)))
                .isEmpty();
        assertLocked(LOCK_NAME1);
    }

    @Test
    void shouldLockExtendAndUnlockWhenClientClockIsBehind() {
        // with client time, lockUntil would be in the past
        skewClientClock(ofHours(-1));
        Optional<SimpleLock> lock = getLockProvider().lock(lockConfig(LOCK_NAME1, ofMinutes(5), ZERO));
        assertThat(lock).isNotEmpty();
        assertLocked(LOCK_NAME1);

        Optional<SimpleLock> extendedLock = lock.get().extend(ofMinutes(5), ZERO);
        assertThat(extendedLock).isNotEmpty();
        assertLocked(LOCK_NAME1);

        extendedLock.get().unlock();
        assertUnlocked(LOCK_NAME1);
    }

    @Test
    void shouldUnlockWhenClientClockIsAhead() {
        Optional<SimpleLock> lock = getLockProvider().lock(lockConfig(LOCK_NAME1, ofMinutes(5), ZERO));
        assertThat(lock).isNotEmpty();

        // with client time, lockUntil would be set an hour into the future
        skewClientClock(ofHours(1));
        lock.get().unlock();
        assertUnlocked(LOCK_NAME1);
        assertThat(getLockProvider().lock(lockConfig(LOCK_NAME1, ofSeconds(5), ZERO)))
                .isNotEmpty();
    }

    private static void skewClientClock(Duration offset) {
        ClockProvider.setClock(Clock.offset(Clock.systemUTC(), offset));
    }
}
