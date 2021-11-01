/**
 * Copyright 2009 the original author or authors.
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
package net.javacrumbs.shedlock.provider.kubernetes;

import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.BeforeEach;

import java.util.Date;

@EnableKubernetesMockClient(crud = true)
public class KubernetesLockProviderTest extends AbstractLockProviderIntegrationTest {

    private KubernetesMockServer server;
    private NamespacedKubernetesClient client;
    private KubernetesLockProvider lockProvider;

    @BeforeEach
    public void setUp() {
        client = server.createClient();
        lockProvider = new KubernetesLockProvider(client, "hostname");
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    @Override
    protected void assertUnlocked(String lockName) {
    }

    @Override
    protected void assertLocked(String lockName) {
    }

    private Date now() {
        return new Date();
    }
}
