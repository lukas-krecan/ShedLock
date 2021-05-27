package net.javacrumbs.shedlock.provider.ignite;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractExtensibleLockProviderIntegrationTest;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;

import static net.javacrumbs.shedlock.provider.ignite.IgniteLockProvider.DEFAULT_SHEDLOCK_CACHE_NAME;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for {@link IgniteLockProvider}.
 */
public class IgniteLockProviderTest extends AbstractExtensibleLockProviderIntegrationTest {
    private static Ignite ignite;
    private static IgniteCache<String, LockValue> cache;

    @BeforeAll
    public static void startIgnite() {
        ignite = Ignition.start();
        cache = ignite.getOrCreateCache(DEFAULT_SHEDLOCK_CACHE_NAME);
    }

    @AfterAll
    public static void stopIgnite() {
        ignite.close();
    }

    @BeforeEach
    public void cleanDb() {
        cache.clear();
    }

    @Override
    protected LockProvider getLockProvider() {
        return new IgniteLockProvider(ignite);
    }

    @Override
    protected void assertUnlocked(String lockName) {
        LockValue val = cache.get(lockName);

        Instant now = Instant.now();

        assertThat(val).isNotNull();
        assertThat(val.getLockUntil()).isBeforeOrEqualTo(now);
        assertThat(val.getLockedAt()).isBeforeOrEqualTo(now);
        assertThat(val.getLockedBy()).isNotEmpty();
    }

    @Override
    protected void assertLocked(String lockName) {
        LockValue val = cache.get(lockName);

        Instant now = Instant.now();

        assertThat(val).isNotNull();
        assertThat(val.getLockUntil()).isAfter(now);
        assertThat(val.getLockedAt()).isBeforeOrEqualTo(now);
        assertThat(val.getLockedBy()).isNotEmpty();
    }
}
