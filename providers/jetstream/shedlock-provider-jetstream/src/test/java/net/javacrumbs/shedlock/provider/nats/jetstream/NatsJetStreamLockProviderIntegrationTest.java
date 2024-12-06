package net.javacrumbs.shedlock.provider.nats.jetstream;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.JetStreamApiException;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.KeyValueEntry;
import java.time.Duration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.support.annotation.Nullable;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class NatsJetStreamLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    @Container
    public static final NatsJetStreamContainer container = new NatsJetStreamContainer();

    private LockProvider lockProvider;
    private Connection connection;

    @BeforeEach
    void createLockProvider() throws Exception {
        var natsUrl = String.format("nats://%s:%d", container.getHost(), container.getFirstMappedPort());
        connection = Nats.connect(Options.builder()
                .server(natsUrl)
                .connectionListener(new ConnectionListener() {

                    private final Logger log = LoggerFactory.getLogger("ConnectionListener");

                    @Override
                    public void connectionEvent(Connection conn, Events type) {
                        log.debug("Received event: {}, on conn: {}", type, conn);
                    }
                })
                .build());

        lockProvider = new NatsJetStreamLockProvider(connection);
    }

    @AfterEach
    void stopLockProvider() throws Exception {
        connection.close();
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
        /** jetstreams smallest allowed unit is 100 milliseconds. */
        this.doTestTimeout(Duration.ofMillis(100), Duration.ofSeconds(2));
    }

    @Override
    @Test
    public void shouldLockAtLeastFor() throws InterruptedException {
        doTestShouldLockAtLeastFor(3_000);
    }

    @Override
    protected void doTestShouldLockAtLeastFor(int sleepForMs) throws InterruptedException {
        Duration lockAtLeastFor = LOCK_AT_LEAST_FOR;
        var lockConfig = lockConfig(LOCK_NAME1, lockAtLeastFor.multipliedBy(2), lockAtLeastFor);

        // Lock for LOCK_AT_LEAST_FOR - we do not expect the lock to be released before
        // this time
        var lock1 = getLockProvider().lock(lockConfig);
        assertThat(lock1).describedAs("Should be locked").isNotEmpty();
        lock1.get().unlock();

        // Even though we have unlocked the lock, it will be held for some time
        assertThat(getLockProvider().lock(lockConfig(LOCK_NAME1)))
                .describedAs(getClass().getName() + "Can not acquire lock, grace period did not pass yet")
                .isEmpty();

        // Let's wait for the lock to be automatically released
        // there is no config to control how fast NATS actually honors its TTL.. ie reaper timers ect
        sleep(lockAtLeastFor.toMillis() * 4);

        // Should be able to acquire now
        // THIS is the difference when compared to the standard test
        var lock3 = getLockProvider().lock(lockConfig);
        assertThat(lock3)
                .describedAs(getClass().getName() + "Can acquire the lock after grace period")
                .isNotEmpty();
        lock3.get().unlock();
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    private @Nullable KeyValueEntry getLock(String lockName) {
        try {
            var bucketName = String.format("SHEDLOCK-%s", lockName);
            return connection.keyValue(bucketName).get("LOCKED");
        } catch (JetStreamApiException e) {
            return null;
        } catch (Exception e) {
            if (e.getCause() instanceof JetStreamApiException ex && ex.getApiErrorCode() == 10059) {
                return null;
            }
            throw new IllegalStateException(e);
        }
    }
}
