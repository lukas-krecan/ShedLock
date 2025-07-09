package net.javacrumbs.shedlock.provider.redis.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import net.javacrumbs.shedlock.support.annotation.Nullable;
import net.javacrumbs.shedlock.test.support.AbstractExtensibleLockProviderIntegrationTest;

/**
 * The fix for this use-case only exists in Redis LockProvider implementations.
 * When we will fix this in all LockProviders, we can move this test to the base class, removing the need for this class.
 */
public abstract class AbstractRedisIntegrationTest extends AbstractExtensibleLockProviderIntegrationTest {

    @Override
    protected void assertLocked(String lockName) {
        assertThat(getLock(lockName)).isNotNull();
    }

    @Override
    protected void assertUnlocked(String lockName) {
        assertThat(getLock(lockName)).isNull();
    }

    @Nullable
    protected abstract String getLock(String lockName);

    protected String buildKey(String lockName, String env) {
        return String.format("%s:%s:%s", "job-lock", env, lockName);
    }

    protected String buildKey(String lockName, String keyPrefix, String env) {
        return String.format("%s:%s:%s", keyPrefix, env, lockName);
    }
}
