package net.javacrumbs.shedlock.provider.nats.jetstream;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.nats.client.Connection;
import io.nats.client.Nats;

@Testcontainers
public class NatsJetStreamLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    @Container
    public static final NatsJetStreamContainer container = new NatsJetStreamContainer();

    static final String ENV = "test";

    private LockProvider lockProvider;

    private Connection nc;

    @BeforeEach
    public void createLockProvider() throws Exception {
        var natsUrl = String.format("nats://%s:%d", container.getHost(), container.getFirstMappedPort());
        nc = Nats.connect(natsUrl);

        lockProvider = new NatsJetStreamLockProvider(nc);
    }

    @Override
    protected void assertUnlocked(String lockName) {
        assertThat(getLock(lockName)).isNull();
    }

    @Override
    protected void assertLocked(String lockName) {
        assertThat(getLock(lockName)).isNotNull();
    }

    @Override
    @Test
    public void shouldTimeout() throws InterruptedException {
        this.doTestTimeout(Duration.ofSeconds(1));
    }

    @Override
    protected void doTestTimeout(Duration lockAtMostFor) throws InterruptedException {
        LockConfiguration configWithShortTimeout = lockConfig(LOCK_NAME1, lockAtMostFor, Duration.ZERO);
        Optional<SimpleLock> lock1 = getLockProvider().lock(configWithShortTimeout);
        assertThat(lock1).isNotEmpty();

        sleep(lockAtMostFor.toMillis() * 2);
        assertUnlocked(LOCK_NAME1);

        Optional<SimpleLock> lock2 =
                getLockProvider().lock(lockConfig(LOCK_NAME1, Duration.ofSeconds(1), Duration.ZERO));
        assertThat(lock2).isNotEmpty();
        lock2.get().unlock();
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    private String getLock(String lockName) {
        try {
            return (String) nc.keyValue(NatsJetStreamLockProvider.buildKey(lockName)).getBucketName();
        } catch (IOException e) {
            fail(e);
        }
        return null;
    }
}
