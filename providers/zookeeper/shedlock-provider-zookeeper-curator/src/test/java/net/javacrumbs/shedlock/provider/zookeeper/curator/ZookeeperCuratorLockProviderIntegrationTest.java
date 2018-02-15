/**
 * Copyright 2009-2017 the original author or authors.
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
package net.javacrumbs.shedlock.provider.zookeeper.curator;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.apache.zookeeper.data.Stat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ZookeeperCuratorLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {
    private TestingServer zkTestServer;
    private CuratorFramework client;
    private ZookeeperCuratorLockProvider zookeeperCuratorLockProvider;

    @Before
    public void startZookeeper() throws Exception {
        zkTestServer = new TestingServer();
        client = newClient();
        zookeeperCuratorLockProvider = new ZookeeperCuratorLockProvider(client);
    }

    @After
    public void stopZookeeper() throws IOException {
        client.close();
        zkTestServer.stop();
    }

    private CuratorFramework newClient() {
        CuratorFramework client = CuratorFrameworkFactory.builder().namespace("MyApp")
            .retryPolicy(new RetryOneTime(2000))
            .connectString(zkTestServer.getConnectString()).build();
        client.start();
        return client;
    }

    @Override
    public void shouldTimeout() throws InterruptedException {
        // pass
    }

    @Test
    public void shouldRemoveLockWhenClientStops() throws InterruptedException {
        LockConfiguration config = lockConfig(LOCK_NAME1);
        Optional<SimpleLock> lock1 = getLockProvider().lock(config);
        assertThat(lock1).isNotEmpty();

        client.close();

        CuratorFramework newClient = newClient();
        Optional<SimpleLock> lock2 = new ZookeeperCuratorLockProvider(newClient).lock(config);
        assertThat(lock2).isNotEmpty();
        newClient.close();
    }

    @Test
    @Override
    public void shouldLockAtLeastFor() throws InterruptedException {
        LockConfiguration configWithAtLeastFor = lockConfig(LOCK_NAME1, LOCK_AT_LEAST_FOR, LOCK_AT_LEAST_FOR);
        assertThatThrownBy(() -> getLockProvider().lock(configWithAtLeastFor)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Override
    protected LockProvider getLockProvider() {
        return zookeeperCuratorLockProvider;
    }

    @Override
    protected void assertUnlocked(String lockName) {
        assertThat(getNodeStat(lockName)).isNull();
    }

    @Override
    protected void assertLocked(String lockName) {
        assertThat(getNodeStat(lockName)).isNotNull();
    }

    private Stat getNodeStat(String lockName) {
        try {
            return client.checkExists().forPath("/shedlock/" + lockName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}