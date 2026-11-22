package net.javacrumbs.shedlock.provider.leases;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractExtensibleLockProviderIntegrationTest;
import org.junit.jupiter.api.BeforeEach;

@EnableKubernetesMockClient(crud = true)
public class LeasesLockProviderIntegrationTest extends AbstractExtensibleLockProviderIntegrationTest {

    // injected by the mock server extension
    static KubernetesClient client;

    private LeasesLockProvider leasesLockProvider;

    @BeforeEach
    void createProvider() {
        leasesLockProvider = new LeasesLockProvider(client, "default");
    }

    @Override
    protected ExtensibleLockProvider getLockProvider() {
        return leasesLockProvider;
    }

    @Override
    protected void assertUnlocked(String lockName) {
        assertThat(leasesLockProvider.isLocked(lockName)).isFalse();
    }

    @Override
    protected void assertLocked(String lockName) {
        assertThat(leasesLockProvider.isLocked(lockName)).isTrue();
    }
}
