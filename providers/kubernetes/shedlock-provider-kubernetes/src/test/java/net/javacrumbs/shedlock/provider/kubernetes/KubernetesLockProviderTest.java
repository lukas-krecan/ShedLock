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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.BeforeEach;

import java.util.Date;
import java.util.Map;

import static net.javacrumbs.shedlock.provider.kubernetes.KubernetesLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.kubernetes.KubernetesLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.kubernetes.KubernetesLockProvider.LOCK_UNTIL;
import static net.javacrumbs.shedlock.provider.kubernetes.KubernetesLockProvider.NAME;
import static org.assertj.core.api.Assertions.assertThat;

@EnableKubernetesMockClient(crud = true)
public class KubernetesLockProviderTest extends AbstractLockProviderIntegrationTest {

    private KubernetesMockServer server;
    private NamespacedKubernetesClient client;
    private KubernetesLockProvider lockProvider;

    @BeforeEach
    public void setUp() {
        client = server.createClient();
        lockProvider = new KubernetesLockProvider(client);
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    @Override
    protected void assertUnlocked(String lockName) {
        ConfigMap configMap = client.configMaps()
            .withName(KubernetesLockProvider.getConfigmapName(lockName))
            .get();
        Map<String, String> data = configMap.getData();
        assertThat(new Date(Long.parseLong(data.get(LOCK_UNTIL)))).isBeforeOrEqualsTo(now());
        assertThat(new Date(Long.parseLong(data.get(LOCKED_AT)))).isBeforeOrEqualsTo(now());
        assertThat(data.get(LOCKED_BY)).isNotBlank();
        assertThat(data.get(NAME)).isEqualTo(lockName);
    }

    @Override
    protected void assertLocked(String lockName) {
        ConfigMap configMap = client.configMaps()
            .withName(KubernetesLockProvider.getConfigmapName(lockName))
            .get();
        Map<String, String> data = configMap.getData();
        assertThat(new Date(Long.parseLong(data.get(LOCK_UNTIL)))).isAfter(now());
        assertThat(new Date(Long.parseLong(data.get(LOCKED_AT)))).isBeforeOrEqualsTo(now());
        assertThat(data.get(LOCKED_BY)).isNotBlank();
        assertThat(data.get(NAME)).isEqualTo(lockName);
    }

    private Date now() {
        return new Date();
    }
}
