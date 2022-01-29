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
package net.javacrumbs.shedlock.provider.etcd.jetcd;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.launcher.Etcd;
import io.etcd.jetcd.launcher.EtcdCluster;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;

public class EtcdLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    private static final EtcdCluster cluster = new Etcd.Builder().withNodes(1).build();

    private EtcdLockProvider lockProvider;
    private KV kvClient;

    @BeforeAll
    static void startCluster() {
        cluster.start();
    }

    @AfterAll
    static void stopCluster() {
        cluster.stop();
    }

    @BeforeEach
    public void createLockProvider() {
        Client client = buildClient();
        // the first call is very slow, so we do this warm up before the actual tests
        warmUpLeaseClient(client);
        kvClient = client.getKVClient();
        lockProvider = new EtcdLockProvider(client);
    }

    @AfterEach
    public void clear() {
        kvClient.delete(buildKey(LOCK_NAME1));
    }

    private void warmUpLeaseClient(Client client) {
        try {
            client.getLeaseClient().grant(2).get();
        } catch (Exception e) {
            fail(e);
        }
    }

    /**
     * Modified for etcd, since its lease grants only suppport TTL in seconds
     */
    @Test
    @Override
    public void shouldTimeout() throws InterruptedException {
        doTestTimeout(ofSeconds(1));
    }

    /**
     * Modified for etcd, since its lease grants only suppport TTL in seconds
     */
    @Test
    @Override
    public void shouldLockAtLeastFor() throws InterruptedException {
        doTestShouldLockAtLeastFor(2000);
    }

    @Override
    protected void assertUnlocked(String lockName) {
        await().timeout(ofSeconds(1)).untilAsserted(() -> assertKeysFound(lockName, 0));
    }

    @Override
    protected void assertLocked(String lockName) {
        assertKeysFound(lockName, 1);
    }

    private void assertKeysFound(String lockName, int expected) {
        ByteSequence key = buildKey(lockName);
        try {
            assertThat(kvClient.get(key).get().getCount()).isEqualTo(expected);
        } catch (Exception ex) {
            fail(ex);
        }
    }

    private ByteSequence buildKey(String lockName) {
        return ByteSequence.from(lockProvider.buildKey(lockName).getBytes());
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    private Client buildClient() {
        return Client.builder().endpoints(cluster.clientEndpoints()).build();
    }

}
