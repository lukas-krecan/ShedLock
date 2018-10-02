/**
 * Copyright 2009-2018 the original author or authors.
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
package net.javacrumbs.shedlock.provider.hazelcast;


import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class HazelcastLockProviderClusterTest {

    private static final String LOCK_NAME_1 = "lock1";

    private static final String LOCK_NAME_2 = "lock2";


    private static HazelcastInstance hazelcastInstance1;

    private static HazelcastInstance hazelcastInstance2;

    private static HazelcastLockProvider lockProvider1;

    private static HazelcastLockProvider lockProvider2;

    @Before
    public void startHazelcast() throws IOException {
        hazelcastInstance1 = Hazelcast.newHazelcastInstance();
        lockProvider1 = new HazelcastLockProvider(hazelcastInstance1);
        hazelcastInstance2 = Hazelcast.newHazelcastInstance();
        lockProvider2 = new HazelcastLockProvider(HazelcastClient.newHazelcastClient());
    }

    @After
    public void resetLockProvider() throws UnknownHostException {
        Hazelcast.shutdownAll();
    }

    @Test
    public void testGetLockByTwoMembersOfCluster() {
        final Optional<SimpleLock> lock1 = lockProvider1.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock1).isNotEmpty();
        final Optional<SimpleLock> lock2 = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock2).isEmpty();
        lock1.get().unlock();
        final Optional<SimpleLock> lock2Bis = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock2Bis).isNotEmpty();
    }

    @Test
    public void testGetLocksByTwoMembersOfCluster() {
        final Optional<SimpleLock> lock11 = lockProvider1.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock11).isNotEmpty();
        final Optional<SimpleLock> lock12 = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock12).isEmpty();
        final Optional<SimpleLock> lock22 = lockProvider2.lock(simpleLockConfig(LOCK_NAME_2));
        assertThat(lock22).isNotEmpty();
        final Optional<SimpleLock> lock21 = lockProvider2.lock(simpleLockConfig(LOCK_NAME_2));
        assertThat(lock21).isEmpty();
        lock11.get().unlock();
        lock22.get().unlock();
        final Optional<SimpleLock> lock12Bis = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock12Bis).isNotEmpty();
        final Optional<SimpleLock> lock21Bis = lockProvider1.lock(simpleLockConfig(LOCK_NAME_2));
        assertThat(lock21Bis).isNotEmpty();
    }

    @Test
    public void testGetLockByLateMemberOfCluster() {
        final Optional<SimpleLock> lock1 = lockProvider1.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock1).isNotEmpty();
        final Optional<SimpleLock> lock2 = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock2).isEmpty();
        final HazelcastLockProvider thirdProvder = new HazelcastLockProvider(Hazelcast.newHazelcastInstance());
        final Optional<SimpleLock> lock3 = thirdProvder.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock3).isEmpty();
    }

    @Test
    public void testGetLockInCluster() throws InterruptedException {
        final Optional<SimpleLock> lock1 = lockProvider1.lock(lockConfig(LOCK_NAME_1, Duration.of(10, SECONDS), Duration.of(5, SECONDS)));
        assertThat(lock1).isNotEmpty();
        final Optional<SimpleLock> lock2 = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock2).isEmpty();
        Thread.sleep(TimeUnit.SECONDS.toMillis(6));
        final Optional<SimpleLock> lock2Bis = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock2Bis).isEmpty();
        Thread.sleep(TimeUnit.SECONDS.toMillis(4));
        final Optional<SimpleLock> lock2Ter = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
        assertThat(lock2Ter).isNotEmpty();
    }

    protected static LockConfiguration simpleLockConfig(final String name) {
        return lockConfig(name, Duration.of(20, SECONDS), Duration.ZERO);
    }

    protected static LockConfiguration lockConfig(final String name, final Duration lockAtMostFor, final Duration lockAtLeastFor) {
        Instant now = Instant.now();
        return new LockConfiguration(name, now.plus(lockAtMostFor), now.plus(lockAtLeastFor));
    }
}