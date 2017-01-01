package net.javacrumbs.shedlock.provider.zookeeper;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class CuratorLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    private TestingServer zkTestServer;
    private CuratorFramework client;
    private CuratorLockProvider curatorLockProvider;

    @Before
    public void startZookeeper() throws Exception {
        zkTestServer = new TestingServer();
        client = newClient();
        curatorLockProvider = new CuratorLockProvider(client);
    }

    private CuratorFramework newClient() {
        CuratorFramework client = CuratorFrameworkFactory.builder().namespace("test/node")
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
        Optional<SimpleLock> lock2 = new CuratorLockProvider(newClient).lock(config);
        assertThat(lock2).isNotEmpty();
        newClient.close();
    }

    @After
    public void stopZookeeper() throws IOException {
        client.close();
        zkTestServer.stop();
    }

    @Override
    protected LockProvider getLockProvider() {
        return curatorLockProvider;
    }

    @Override
    protected void assertUnlocked(String lockName) {

    }

    @Override
    protected void assertLocked(String lockName) {

    }
}