package net.javacrumbs.shedlock.provider.nats.jetstream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static net.javacrumbs.shedlock.provider.nats.jetstream.NatsJetStreamContainer.NATS_IMAGE;
import static net.javacrumbs.shedlock.test.support.DockerCleaner.removeImageInCi;
import static org.assertj.core.api.Assertions.assertThat;

import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.KeyValueEntry;
import java.time.Instant;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class NatsJetStreamLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    @Container
    public static final NatsJetStreamContainer container = new NatsJetStreamContainer();

    private LockProvider lockProvider;
    private Connection connection;

    @BeforeEach
    void createLockProvider() throws Exception {
        var natsUrl = String.format("nats://%s:%d", container.getHost(), container.getFirstMappedPort());
        connection = Nats.connect(Options.builder().server(natsUrl).build());

        lockProvider = new NatsJetStreamLockProvider(connection);
    }

    @AfterEach
    void stopLockProvider() throws Exception {
        connection.close();
    }

    @AfterAll
    static void removeImage() {
        removeImageInCi(NATS_IMAGE.asCanonicalNameString());
    }

    @Override
    protected void assertUnlocked(String lockName) {
        var entry = getLock(lockName);
        if (entry != null) {
            // If entry exists, check if the timestamp has expired
            var lockUntil = Instant.parse(new String(entry.getValue(), UTF_8));
            assertThat(lockUntil).isBefore(ClockProvider.now());
        }
    }

    @Override
    protected void assertLocked(String lockName) {
        assertThat(getLock(lockName)).isNotNull();
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    private KeyValueEntry getLock(final String lockName) {
        try {
            return connection.keyValue("shedlock-locks").get(lockName);
        } catch (JetStreamApiException e) {
            if (e.getApiErrorCode() == 10059) { // Key not found
                return null;
            }
            throw new IllegalStateException(e);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
