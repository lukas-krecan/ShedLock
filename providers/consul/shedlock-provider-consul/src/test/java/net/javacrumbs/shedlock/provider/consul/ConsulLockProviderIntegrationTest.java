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

class ConsulLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    public static ConsulClient consulClient;
    private static ConsulProcess consul;

    @BeforeAll
    public static void startConsul() {
        consul = ConsulStarterBuilder.consulStarter().build().start();
        consulClient = new ConsulClient(consul.getAddress(), consul.getHttpPort());
    }

    @AfterAll
    public static void stopConsul() {
        consul.close();
    }

    @BeforeEach
    public void resetConsul() {
        consul.reset();
    }

    @Override
    protected LockProvider getLockProvider() {
        return new ConsulLockProvider(consulClient);
    }

    @Override
    protected void assertUnlocked(final String lockName) {
        GetValue leader = getLockValue(lockName);
        assertThat(leader).isNull();
    }

    @Override
    protected void assertLocked(final String lockName) {
        GetValue leader = getLockValue(lockName);
        assertThat(Optional.ofNullable(leader).map(GetValue::getSession)).isNotEmpty();
    }

    private GetValue getLockValue(String lockName) {
        return consulClient.getKVValue(lockName + "-leader").getValue();
    }

    @Test
    @Override
    public void shouldTimeout() throws InterruptedException {
        // as consul has 10 seconds ttl minimum and has double ttl unlocking time, you have to wait for 20 seconds for the unlock time.
        Duration lockAtMostFor = Duration.ofSeconds(11);
        LockConfiguration configWithShortTimeout = lockConfig(LOCK_NAME1, lockAtMostFor, Duration.ZERO);
        Optional<SimpleLock> lock1 = getLockProvider().lock(configWithShortTimeout);
        assertThat(lock1).isNotEmpty();

        sleep(lockAtMostFor.multipliedBy(2).toMillis() + 100);
        assertUnlocked(LOCK_NAME1);

        Optional<SimpleLock> lock2 = getLockProvider().lock(lockConfig(LOCK_NAME1, Duration.ofMillis(50), Duration.ZERO));
        assertThat(lock2).isNotEmpty();
        lock2.get().unlock();
    }

    @Test
    public void shouldNotTimeoutIfLessThanMinTtlPassed() throws InterruptedException {
        Duration lockAtMostFor = Duration.ofSeconds(1);
        LockConfiguration configWithShortTimeout = lockConfig(LOCK_NAME1, lockAtMostFor, Duration.ZERO);
        Optional<SimpleLock> lock1 = getLockProvider().lock(configWithShortTimeout);
        assertThat(lock1).isNotEmpty();

        sleep(lockAtMostFor.multipliedBy(2).toMillis() + 100);
        assertLocked(LOCK_NAME1);
    }
}
