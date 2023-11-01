package net.javacrumbs.shedlock.provider.redis.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import io.quarkus.test.junit.QuarkusTest;
import javax.inject.Inject;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractExtensibleLockProviderIntegrationTest;
import org.junit.jupiter.api.BeforeEach;

@QuarkusTest
public class QuarkusRedisLockProviderIntegrationTest extends AbstractExtensibleLockProviderIntegrationTest {

    @Inject
    LockProvider lockProvider;

    @Inject
    RedisDataSource dataSource;

    private ValueCommands<String, String> values;

    @BeforeEach
    public void beforeEach() {
        this.values = dataSource.value(String.class);
    }

    @Override
    protected void assertUnlocked(String lockName) {
        assertThat(getLock(lockName)).isNull();
    }

    @Override
    protected void assertLocked(String lockName) {
        assertThat(getLock(lockName)).isNotNull();
    }

    private String getLock(String lockName) {
        return values.get("job-lock:my-app:" + lockName);
    }

    @Override
    protected ExtensibleLockProvider getLockProvider() {
        return (ExtensibleLockProvider) lockProvider;
    }
}
