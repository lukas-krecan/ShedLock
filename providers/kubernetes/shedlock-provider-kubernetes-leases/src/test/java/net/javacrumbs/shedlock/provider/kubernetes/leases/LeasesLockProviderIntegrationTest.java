package net.javacrumbs.shedlock.provider.kubernetes.leases;

import static org.assertj.core.api.Assertions.assertThat;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.test.support.AbstractExtensibleLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@EnableKubernetesMockClient(crud = true)
public class LeasesLockProviderIntegrationTest extends AbstractExtensibleLockProviderIntegrationTest {

    // injected by the mock server extension
    @SuppressWarnings("NullAway.Init")
    static KubernetesClient client;

    private LeasesLockProvider leasesLockProvider;

    @BeforeEach
    void createProvider() {
        leasesLockProvider = new LeasesLockProvider(client, "default");
    }

    @AfterEach
    void resetClock() {
        ClockProvider.setClock(Clock.systemUTC());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldNotExtendLockAcquiredByAnotherExecution(boolean sameProvider) {
        SimpleLock original = lock(Duration.ofSeconds(1));
        SimpleLock successor = acquireAfterExpiry(sameProvider);
        var lease = client.leases().inNamespace("default").withName(LOCK_NAME1).get();

        assertThat(original.extend(Duration.ofMinutes(5), Duration.ZERO)).isEmpty();

        assertThat(client.leases().inNamespace("default").withName(LOCK_NAME1).get())
                .isEqualTo(lease);
        assertLocked(LOCK_NAME1);
        successor.unlock();
        assertUnlocked(LOCK_NAME1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldNotUnlockLockAcquiredByAnotherExecution(boolean sameProvider) {
        SimpleLock original = lock(Duration.ofSeconds(1));
        SimpleLock successor = acquireAfterExpiry(sameProvider);
        var lease = client.leases().inNamespace("default").withName(LOCK_NAME1).get();

        original.unlock();

        assertThat(client.leases().inNamespace("default").withName(LOCK_NAME1).get())
                .isEqualTo(lease);
        assertThat(leasesLockProvider.lock(lockConfig(LOCK_NAME1))).isEmpty();
        successor.unlock();
        assertUnlocked(LOCK_NAME1);
    }

    private SimpleLock acquireAfterExpiry(boolean sameProvider) {
        ClockProvider.setClock(Clock.fixed(ClockProvider.now().plusSeconds(2), ZoneOffset.UTC));
        LeasesLockProvider nextProvider =
                sameProvider ? leasesLockProvider : new LeasesLockProvider(client, "default", "other-holder");
        return nextProvider.lock(lockConfig(LOCK_NAME1)).orElseThrow();
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
