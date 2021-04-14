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
package net.javacrumbs.shedlock.provider.zookeeper.curator;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.apache.zookeeper.CreateMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class ZookeeperCuratorLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {
    private TestingServer zkTestServer;
    private CuratorFramework client;
    private ZookeeperCuratorLockProvider zookeeperCuratorLockProvider;

    @BeforeEach
    public void startZookeeper() throws Exception {
        zkTestServer = new TestingServer();
        client = newClient();
        zookeeperCuratorLockProvider = new ZookeeperCuratorLockProvider(client);
    }

    @AfterEach
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

    @Test
    public void shouldNotOverwriteLockCreatedByPreviousVersion() throws Exception {
        client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(getNodePath(LOCK_NAME1));

        Optional<SimpleLock> lock1 = zookeeperCuratorLockProvider.lock(lockConfig(LOCK_NAME1));
        assertThat(lock1).isEmpty();

        client.delete().forPath(getNodePath(LOCK_NAME1));
        Optional<SimpleLock> lock2 = zookeeperCuratorLockProvider.lock(lockConfig(LOCK_NAME1));
        assertThat(lock2).isNotEmpty();
    }

    @Override
    protected LockProvider getLockProvider() {
        return zookeeperCuratorLockProvider;
    }

    @Override
    protected void assertUnlocked(String lockName) {
        try {
            assertThat(zookeeperCuratorLockProvider.isLocked(getNodePath(lockName))).isFalse();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    protected void assertLocked(String lockName) {
        try {
            assertThat(zookeeperCuratorLockProvider.isLocked(getNodePath(lockName))).isTrue();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String getNodePath(String lockName) {
        return zookeeperCuratorLockProvider.getNodePath(lockName);
    }
}
