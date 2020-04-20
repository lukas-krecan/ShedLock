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
package net.javacrumbs.shedlock.provider.geode;

import junitparams.JUnitParamsRunner;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.apache.geode.test.dunit.IgnoredException;
import org.apache.geode.test.dunit.rules.ClientVM;
import org.apache.geode.test.dunit.rules.ClusterStartupRule;
import org.apache.geode.test.dunit.rules.DistributedRule;
import org.apache.geode.test.dunit.rules.MemberVM;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JUnitParamsRunner.class)
public class GeodeLockProviderClusterTest implements Serializable {

    @ClassRule
    public static final ClusterStartupRule clusterStartupRule = new ClusterStartupRule(4).withLogFile();

    @ClassRule
    public static final DistributedRule distributedRule = new DistributedRule(4);

    private static int LOCK_TIME_SEC = 5;

    private static final String LOCK_NAME_1 = "lock1";

    private static final String LOCK_NAME_2 = "lock2";

    private static GeodeLockProvider lockProvider1;

    private static GeodeLockProvider lockProvider2;

    public static MemberVM server1;
    public static ClientVM client1;
    public static ClientVM client2;
    public static ClientVM clientVM3;

    @Before
    public void setUp() throws Exception {
        if (server1 == null) {
            server1 = GeodeTestUtils.startServer(clusterStartupRule, 1);
        }
        if (client1 == null) {
            client1 = GeodeTestUtils.startClient(clusterStartupRule, 2);
        }
        if (client2 == null) {
            client2 = GeodeTestUtils.startClient(clusterStartupRule, 3);
        }
        Thread.sleep(Duration.of(LOCK_TIME_SEC, SECONDS).toMillis());
    }

    @After
    public void tearDown() throws Exception {
        IgnoredException.removeAllExpectedExceptions();
    }

    @Test
    public void testLockUnlock() throws Exception {
        client1.invoke(() -> {
            lockProvider1 = new GeodeLockProvider(ClusterStartupRule.getClientCache());
            final Optional<SimpleLock> lock1 = lockProvider1.lock(simpleLockConfig(LOCK_NAME_1));
            assertThat(lock1).isNotEmpty();
            lock1.get().unlock();
            Optional<SimpleLock> lock2 = lockProvider1.lock(simpleLockConfig(LOCK_NAME_1));
            assertThat(lock2).isNotEmpty();
            lock2.get().unlock();
        });
    }

    @Test
    public void getLockByTwoMembersOfClusterTest() throws Exception {
        client1.invoke(() -> {
            lockProvider1 = new GeodeLockProvider(ClusterStartupRule.getClientCache());
            final Optional<SimpleLock> lock1 = lockProvider1.lock(simpleLockConfig(LOCK_NAME_1));
            assertThat(lock1).isNotEmpty();
        });
        client2.invoke(() -> {
            lockProvider2 = new GeodeLockProvider(ClusterStartupRule.getClientCache());
            final Optional<SimpleLock> lock2 = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
            assertThat(lock2).isEmpty();
        });
        //lock unlocked automatically
        Thread.sleep(Duration.of(LOCK_TIME_SEC, SECONDS).toMillis());
        client2.invoke(() -> {
            final Optional<SimpleLock> lock2Bis = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
            assertThat(lock2Bis).isNotEmpty();
            lock2Bis.get().unlock();
        });
    }

    @Test
    public void testGetRLocksByTwoMembersOfCluster() {
        client1.invoke(() -> {
            lockProvider1 = new GeodeLockProvider(ClusterStartupRule.getClientCache());
            final Optional<SimpleLock> lock11 = lockProvider1.lock(simpleLockConfig(LOCK_NAME_1));
            assertThat(lock11).isNotEmpty();
        });
        client2.invoke(() -> {
            lockProvider2 = new GeodeLockProvider(ClusterStartupRule.getClientCache());
            final Optional<SimpleLock> lock12 = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
            assertThat(lock12).isEmpty();
            final Optional<SimpleLock> lock22 = lockProvider2.lock(simpleLockConfig(LOCK_NAME_2));
            assertThat(lock22).isNotEmpty();
            //Same Client can lock the same Resource again and again as long as it has the lock
            final Optional<SimpleLock> lock21 = lockProvider2.lock(simpleLockConfig(LOCK_NAME_2));
            assertThat(lock21).isNotEmpty();
            lock22.get().unlock();
            Thread.sleep(10000);
            final Optional<SimpleLock> lock12Bis = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
            assertThat(lock12Bis).isNotEmpty();
        });
        client1.invoke(() -> {
            final Optional<SimpleLock> lock21Bis = lockProvider1.lock(simpleLockConfig(LOCK_NAME_2));
            assertThat(lock21Bis).isNotEmpty();
        });
    }

    @Test
    public void testGetLockByLateMemberOfCluster() throws Exception {
        client1.invoke(() -> {
            lockProvider1 = new GeodeLockProvider(ClusterStartupRule.getClientCache());
            final Optional<SimpleLock> lock1 = lockProvider1.lock(simpleLockConfig(LOCK_NAME_1));
            assertThat(lock1).isNotEmpty();
        });
        client2.invoke(() -> {
            lockProvider2 = new GeodeLockProvider(ClusterStartupRule.getClientCache());
            final Optional<SimpleLock> lock2 = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
            assertThat(lock2).isEmpty();
        });
        clientVM3 = GeodeTestUtils.startClient(clusterStartupRule, 0);
        clientVM3.invoke(() -> {
            GeodeLockProvider thirdProvder = new GeodeLockProvider(ClusterStartupRule.getClientCache());
            final Optional<SimpleLock> lock3 = thirdProvder.lock(simpleLockConfig(LOCK_NAME_1));
            assertThat(lock3).isEmpty();
        });
    }

    @Test
    public void testGetLockInCluster() throws InterruptedException {
        client1.invoke(() -> {
            lockProvider1 = new GeodeLockProvider(ClusterStartupRule.getClientCache());
            final Optional<SimpleLock> lock1 = lockProvider1.lock(GeodeTestUtils.lockConfig(LOCK_NAME_1, Duration.of(10, SECONDS), Duration.of(5, SECONDS)));
            assertThat(lock1).isNotEmpty();
        });
        client2.invoke(() -> {
            lockProvider2 = new GeodeLockProvider(ClusterStartupRule.getClientCache());
            final Optional<SimpleLock> lock2 = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
            assertThat(lock2).isEmpty();
        });
        Thread.sleep(TimeUnit.SECONDS.toMillis(6));
        client2.invoke(() -> {
            lockProvider2 = new GeodeLockProvider(ClusterStartupRule.getClientCache());
            final Optional<SimpleLock> lock2Bis = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
            assertThat(lock2Bis).isEmpty();
            Thread.sleep(TimeUnit.SECONDS.toMillis(4));
            final Optional<SimpleLock> lock2Ter = lockProvider2.lock(simpleLockConfig(LOCK_NAME_1));
            assertThat(lock2Ter).isNotEmpty();
        });
    }

    public static LockConfiguration simpleLockConfig(final String name) {
        return GeodeTestUtils.lockConfig(name, Duration.of(LOCK_TIME_SEC, SECONDS), Duration.ZERO);
    }
}
