package net.javacrumbs.shedlock.provider.memcached.spy;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import net.spy.memcached.AddrUtil;
import net.spy.memcached.MemcachedClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;


@Testcontainers
public class MemcachedLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    @Container
    public static final MemcachedContainer container = new MemcachedContainer();

    static final String ENV = "test";

    private LockProvider lockProvider;

    private MemcachedClient memcachedClient;

    @BeforeEach
    public void createLockProvider() throws IOException {
        memcachedClient = new MemcachedClient(
            AddrUtil.getAddresses(container.getContainerIpAddress() + ":" + container.getFirstMappedPort())
        );

        lockProvider = new MemcachedLockProvider(memcachedClient, ENV);
    }


    @Override
    protected void assertUnlocked(String lockName) {
        assertThat(getLock(lockName)).isNull();
    }

    @Override
    protected void assertLocked(String lockName) {
        assertThat(getLock(lockName)).isNotNull();
    }


    @Test
    public void shouldTimeout() throws InterruptedException {
        this.doTestTimeout(Duration.ofSeconds(1));
    }

    /**
     * memcached smallest unit is second.
     */
    @Override
    protected void doTestTimeout(Duration lockAtMostFor) throws InterruptedException {
        LockConfiguration configWithShortTimeout = lockConfig(LOCK_NAME1, lockAtMostFor, Duration.ZERO);
        Optional<SimpleLock> lock1 = getLockProvider().lock(configWithShortTimeout);
        assertThat(lock1).isNotEmpty();

        sleep(lockAtMostFor.toMillis() * 2);
        assertUnlocked(LOCK_NAME1);

        Optional<SimpleLock> lock2 = getLockProvider().lock(lockConfig(LOCK_NAME1, Duration.ofSeconds(1), Duration.ZERO));
        assertThat(lock2).isNotEmpty();
        lock2.get().unlock();
    }


    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    private String getLock(String lockName) {
        return (String) memcachedClient.get(MemcachedLockProvider.buildKey(lockName, ENV));
    }

}
