package net.javacrumbs.shedlock.provider.nats.jetstream;

import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.api.KeyValueConfiguration;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lock Provider for NATS JetStream
 *
 * @see <a href="https://docs.nats.io/nats-concepts/jetstream">JetStream</a>
 */
public class NatsJetStreamLockProvider implements LockProvider {

    private final Logger log = LoggerFactory.getLogger(NatsJetStreamLockProvider.class);

    private final Connection connection;

    /**
     * Create NatsJetStreamLockProvider
     *
     * @param connection
     *                   io.nats.client.Connection
     */
    public NatsJetStreamLockProvider(Connection connection) {
        this.connection = connection;
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        var bucketName = String.format("SHEDLOCK-%s", lockConfiguration.getName());
        log.debug("Attempting lock for bucketName: {}", bucketName);
        try {
            var lockTime = lockConfiguration.getLockAtMostFor();

            // nats cannot accept below 100ms
            if (lockTime.toMillis() < 100) {
                log.debug(
                        "NATS must be above 100ms for smallest locktime, correcting {}ms to 100ms!",
                        lockTime.toMillis());
                lockTime = Duration.ofMillis(100L);
            }

            connection
                    .keyValueManagement()
                    .create(KeyValueConfiguration.builder()
                            .name(bucketName)
                            .ttl(lockTime)
                            .build());
            connection
                    .keyValue(bucketName)
                    .create(
                            "LOCKED",
                            LockContentHandler.writeContent(new LockContent(
                                    lockConfiguration.getLockAtLeastUntil(), lockConfiguration.getLockAtMostUntil())));

            log.debug("Accuired lock for bucketName: {}", bucketName);

            return Optional.of(new NatsJetStreamLock(connection, lockConfiguration));
        } catch (JetStreamApiException e) {
            if (e.getApiErrorCode() == 10058 || e.getApiErrorCode() == 10071) {
                log.debug("Rejected lock for bucketName: {}, message: {}", bucketName, e.getMessage());
                return Optional.empty();
            }
            log.warn("Rejected lock for bucketName: {}", bucketName);
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
