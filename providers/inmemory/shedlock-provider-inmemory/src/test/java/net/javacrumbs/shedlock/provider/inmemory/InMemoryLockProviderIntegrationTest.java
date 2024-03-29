package net.javacrumbs.shedlock.provider.inmemory;

import static org.assertj.core.api.Assertions.assertThat;

import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractExtensibleLockProviderIntegrationTest;

public class InMemoryLockProviderIntegrationTest extends AbstractExtensibleLockProviderIntegrationTest {

    private final InMemoryLockProvider inMemoryLockProvider = new InMemoryLockProvider();

    @Override
    protected ExtensibleLockProvider getLockProvider() {
        return inMemoryLockProvider;
    }

    @Override
    protected void assertUnlocked(String lockName) {
        assertThat(inMemoryLockProvider.isLocked(lockName)).isFalse();
    }

    @Override
    protected void assertLocked(String lockName) {
        assertThat(inMemoryLockProvider.isLocked(lockName)).isTrue();
    }
}
