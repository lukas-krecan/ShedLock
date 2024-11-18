package net.javacrumbs.shedlock.provider.nats.jetstream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;

import static java.lang.Thread.sleep;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.nats.client.Connection;
import io.nats.client.ConnectionListener;
import io.nats.client.JetStreamApiException;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.KeyValueEntry;

@Testcontainers
public class NatsJetStreamLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    @Container
    public static final NatsJetStreamContainer container = new NatsJetStreamContainer();

    private LockProvider lockProvider;
    private Connection connection;

    @BeforeEach
    public void createLockProvider() throws Exception {
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
    public void stopLockProvider() throws Exception {
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
        this.doTestTimeout(Duration.ofMillis(100));
    }

    @Override
    protected void doTestTimeout(Duration lockAtMostFor) throws InterruptedException {
        final var SHORT_LOCK_NAME = UUID.randomUUID().toString();

        var configWithShortTimeout = lockConfig(SHORT_LOCK_NAME, lockAtMostFor, Duration.ZERO);
        var lock1 = getLockProvider().lock(configWithShortTimeout);
        assertThat(lock1).isNotEmpty();

        // there is no config to control how fast NATS actually honors its TTL.. ie reaper timers ect
        sleep(Duration.ofSeconds(2).toMillis());
        assertUnlocked(SHORT_LOCK_NAME);

        var lock2 = getLockProvider().lock(lockConfig(SHORT_LOCK_NAME, lockAtMostFor, Duration.ZERO));
        assertThat(lock2).isNotEmpty();
        lock2.get().unlock();
    }

    @Override
    @Test
    public void shouldLockAtLeastFor() throws InterruptedException {
        doTestShouldLockAtLeastFor(100);
    }

    @Override
    protected void doTestShouldLockAtLeastFor(int sleepForMs) throws InterruptedException {
        final var ATLEAST_LOCK_NAME = UUID.randomUUID().toString();
        final var lockAtLeastFor = Duration.ofSeconds(1L);

        var lockConfig = lockConfig(ATLEAST_LOCK_NAME, lockAtLeastFor.multipliedBy(2), lockAtLeastFor);

        // Lock for LOCK_AT_LEAST_FOR - we do not expect the lock to be released before
        // this time
        var lock1 =
                getLockProvider().lock(lockConfig);
        assertThat(lock1).describedAs("Should be locked").isNotEmpty();
        lock1.get().unlock();

        // Even though we have unlocked the lock, it will be held for some time
        assertThat(getLockProvider().lock(lockConfig))
                .describedAs(getClass().getName() + "Can not acquire lock, grace period did not pass yet")
                .isEmpty();

        // Let's wait for the lock to be automatically released
        // there is no config to control how fast NATS actually honors its TTL.. ie reaper timers ect
        sleep(lockAtLeastFor.toMillis() * 4);

        // Should be able to acquire now
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

    private KeyValueEntry getLock(String lockName) {
        try {
            var bucketName = String.format("SHEDLOCK-%s", lockName);
            var keyValueEntry = connection.keyValue(bucketName).get("LOCKED");
            if (keyValueEntry != null) {

            }
            return keyValueEntry;
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
