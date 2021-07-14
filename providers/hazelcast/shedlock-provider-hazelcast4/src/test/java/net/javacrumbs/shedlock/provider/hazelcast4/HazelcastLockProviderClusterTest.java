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
package net.javacrumbs.shedlock.provider.hazelcast4;


import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.Hazelcast;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@Disabled
public class HazelcastLockProviderClusterTest {

    private final String LOCK_NAME_1 = UUID.randomUUID().toString();

    private final String LOCK_NAME_2 = UUID.randomUUID().toString();

    private static HazelcastLockProvider lockProvider1;

    private static HazelcastLockProvider lockProvider2;

    @BeforeEach
    public void startHazelcast() {
        lockProvider1 = new HazelcastLockProvider(Hazelcast.newHazelcastInstance());
        lockProvider2 = new HazelcastLockProvider(HazelcastClient.newHazelcastClient());
    }

    @AfterEach
    public void resetLockProvider() {
        Hazelcast.shutdownAll();
    }

    @Test
    public void testGetLockByTwoMembersOfCluster() {
        Optional<SimpleLock> lock1 = lockProvider1.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock1).isNotEmpty();
        Optional<SimpleLock> lock2 = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock2).isEmpty();
        lock1.get().unlock();
        Optional<SimpleLock> lock2Bis = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock2Bis).isNotEmpty();
    }

    @Test
    public void testGetLocksByTwoMembersOfCluster() {
        Optional<SimpleLock> lock11 = lockProvider1.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock11).isNotEmpty();
        Optional<SimpleLock> lock12 = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock12).isEmpty();
        Optional<SimpleLock> lock22 = lockProvider2.lock(simpleLockConfig(LOCK_NAME_2));
        assertThat(lock22).isNotEmpty();
        Optional<SimpleLock> lock21 = lockProvider2.lock(simpleLockConfig(LOCK_NAME_2));
        assertThat(lock21).isEmpty();
        lock11.get().unlock();
        lock22.get().unlock();
        assertUnlocked(lockProvider2, LOCK_NAME_1);
        assertUnlocked(lockProvider1, LOCK_NAME_2);
    }

    private void assertUnlocked(HazelcastLockProvider lockProvider,  String lockName) {
        HazelcastLock lock = lockProvider.getLock(lockName);
        if (lock != null) {
            Instant now = now();
            if (!lock.isExpired(now)) {
                fail("Expected to be unlocked but got lock " + lock + " current time is " + now);
            }
        }
    }

    @Test
    public void testGetLockByLateMemberOfCluster() {
        Optional<SimpleLock> lock1 = lockProvider1.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock1).isNotEmpty();
        Optional<SimpleLock> lock2 = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock2).isEmpty();
        HazelcastLockProvider thirdProvder = new HazelcastLockProvider(Hazelcast.newHazelcastInstance());
        Optional<SimpleLock> lock3 = thirdProvder.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock3).isEmpty();
    }

    @Test
    public void testGetLockInCluster() throws InterruptedException {
        Optional<SimpleLock> lock1 = lockProvider1.lock(lockConfig(LOCK_NAME_1, Duration.of(5, SECONDS), Duration.of(3, SECONDS)));
        assertThat(lock1).isNotEmpty();
        Optional<SimpleLock> lock2 = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock2).isEmpty();
        sleepSeconds(3);
        Optional<SimpleLock> lock2Bis = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock2Bis).isEmpty();
        sleepSeconds(2);
        Optional<SimpleLock> lock2Ter = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock2Ter).isNotEmpty();
    }

    private void sleepSeconds(int i) throws InterruptedException {
        Thread.sleep(TimeUnit.SECONDS.toMillis(i));
    }

    protected static LockConfiguration simpleLockConfig(String name) {
        return lockConfig(name, Duration.of(20, SECONDS), Duration.ZERO);
    }

    protected static LockConfiguration lockConfig(String name, Duration lockAtMostFor, Duration lockAtLeastFor) {
        return new LockConfiguration(now(), name, lockAtMostFor, lockAtLeastFor);
    }
}
