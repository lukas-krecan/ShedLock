/**
 * Copyright 2009-2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
import io.etcd.jetcd.test.EtcdClusterExtension;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;

import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class EtcdLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    @RegisterExtension
    static EtcdClusterExtension etcdCluster = new EtcdClusterExtension("it-cluster", 1);

    private LockProvider lockProvider;
    private KV kvClient;

    @BeforeEach
    public void createLockProvider() {
        Client client = buildClient();
        // the first call is very slow, so we do this warm up before the actual tests
        warmUpLeaseClient(client);
        kvClient = client.getKVClient();
        lockProvider = new EtcdLockProvider(client);
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
        doTestTimeout(Duration.ofSeconds(1));
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
        ByteSequence key = ByteSequence.from(EtcdLockProvider.buildKey(lockName, EtcdLockProvider.ENV_DEFAULT).getBytes());
        try {
            sleep(500);
            assertEquals(0, kvClient.get(key).get().getCount());
        } catch (Exception ex) {
            fail(ex);
        }
    }

    @Override
    protected void assertLocked(String lockName) {
        ByteSequence key = ByteSequence.from(EtcdLockProvider.buildKey(lockName, EtcdLockProvider.ENV_DEFAULT).getBytes());
        try {
            assertEquals(1, kvClient.get(key).get().getCount());
        } catch (Exception ex) {
            fail(ex);
        }
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    private Client buildClient() {
        return Client.builder().endpoints(etcdCluster.getClientEndpoints()).build();
    }

}
