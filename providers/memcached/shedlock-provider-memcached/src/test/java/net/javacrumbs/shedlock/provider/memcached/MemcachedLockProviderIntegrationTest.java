package net.javacrumbs.shedlock.provider.memcached;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import net.spy.memcached.AddrUtil;
import net.spy.memcached.MemcachedClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;


@Testcontainers
public class MemcachedLockProviderIntegrationTest {

    @Container
    public static final MemcachedContainer container = new MemcachedContainer(11211);

    static final String ENV = "test";


    @Nested
    class Memcached extends AbstractLockProviderIntegrationTest {

        private LockProvider lockProvider;

        private MemcachedClient memcachedClient;

        @BeforeEach
        public void createLockProvider() throws IOException {
            memcachedClient = new MemcachedClient(
                AddrUtil.getAddresses(
                    container.getContainerIpAddress()+":"+container.getFirstMappedPort()));

            lockProvider = new MemcachedLockProvider(memcachedClient, ENV);
        }

        @Override
        protected LockProvider getLockProvider() {
            return lockProvider;
        }

        private String getLock(String lockName) {
            return (String) memcachedClient.get(MemcachedLockProvider.buildKey(lockName, ENV));
        }

        @Override
        protected void assertUnlocked(String lockName) {
            assertThat(getLock(lockName)).isNull();
        }

        @Override
        protected void assertLocked(String lockName) {
            assertThat(getLock(lockName)).isNotNull();
        }
    }


}
