package net.javacrumbs.shedlock.provider.consul;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.kv.model.GetValue;
import com.pszymczyk.consul.ConsulProcess;
import com.pszymczyk.consul.ConsulStarterBuilder;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;

class ConsulSchedulableLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    private static ConsulClient CONSUL_CLIENT;
    private static ConsulProcess consul;
    private ConsulSchedulableLockProvider lockProvider;

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("org.slf4j.simpleLogger.log.net.javacrumbs.shedlock", "DEBUG");
        consul = ConsulStarterBuilder.consulStarter().build().start();
        CONSUL_CLIENT = new ConsulClient(consul.getAddress(), consul.getHttpPort());
    }

    @AfterAll
    public static void afterAll() {
        consul.close();
        System.clearProperty("org.slf4j.simpleLogger.log.net.javacrumbs.shedlock");
    }

    @BeforeEach
    public void setUp() {
        consul.reset();
        lockProvider = new ConsulSchedulableLockProvider(CONSUL_CLIENT);
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    @Override
    protected void assertUnlocked(final String lockName) {
        GetValue leader = CONSUL_CLIENT.getKVValue(lockName + "-leader").getValue();
        assertThat(leader).isNull();
    }

    @Override
    protected void assertLocked(final String lockName) {
        GetValue leader = CONSUL_CLIENT.getKVValue(lockName + "-leader").getValue();
        assertThat(Optional.ofNullable(leader).map(GetValue::getSession)).isNotEmpty();
    }

    @Test
    void shouldRenewSessionUntilAtMostForPassed() throws InterruptedException {
        // consul has 10 seconds minimum TTL so for the integration testing purposes we need to have lockedAtMostFor higher than 10
        LockConfiguration configWithShortTimeout = lockConfig(LOCK_NAME1, Duration.ofSeconds(12), Duration.ZERO);
        LockProvider lockProvider = getLockProvider();
        Optional<SimpleLock> lock1 = lockProvider.lock(configWithShortTimeout);
        assertThat(lock1).isNotEmpty();

        sleep(11000);
        assertLocked(LOCK_NAME1);

        sleep(1000 + 100);
        assertUnlocked(LOCK_NAME1);
    }
}
