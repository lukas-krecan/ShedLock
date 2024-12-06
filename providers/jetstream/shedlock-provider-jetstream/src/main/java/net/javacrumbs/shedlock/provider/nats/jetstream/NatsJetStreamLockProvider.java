package net.javacrumbs.shedlock.provider.nats.jetstream;

import static net.javacrumbs.shedlock.core.ClockProvider.now;

import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.api.KeyValueConfiguration;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lock Provider for NATS JetStream
 *
 * @see <a href="https://docs.nats.io/nats-concepts/jetstream">JetStream</a>
 */
public class NatsJetStreamLockProvider implements LockProvider, AutoCloseable {

    private final Logger log = LoggerFactory.getLogger(NatsJetStreamLockProvider.class);

    private final ScheduledExecutorService unlockScheduler = Executors.newSingleThreadScheduledExecutor();

    private final Connection connection;

    /**
     * Create NatsJetStreamLockProvider
     *
     * @param connection
     *                   io.nats.client.Connection
     */
    public NatsJetStreamLockProvider(@NonNull Connection connection) {
        this.connection = connection;
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        var bucketName = String.format("SHEDLOCK-%s", lockConfiguration.getName());
        log.debug("Attempting lock for bucketName: {}", bucketName);
        try {
            var lockTime = lockConfiguration.getLockAtMostFor();

            // nats cannot accept below 100ms
            if (lockTime.toMillis() < 100L) {
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
            connection.keyValue(bucketName).create("LOCKED", "ShedLock internal value. Do not touch.".getBytes());

            log.debug("Acquired lock for bucketName: {}", bucketName);

            return Optional.of(new NatsJetStreamLock(lockConfiguration, this));
        } catch (JetStreamApiException e) {
            if (e.getApiErrorCode() == 10071) {
                log.debug("Rejected lock for bucketName: {}, message: {}", bucketName, e.getMessage());
                return Optional.empty();
            } else if (e.getApiErrorCode() == 10058) {
                log.warn(
                        "Settings on the bucket TTL does not match configuration. Manually delete the bucket on NATS server, or revert lock settings!");
                return Optional.empty();
            }
            log.warn("Rejected lock for bucketName: {}", bucketName);
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    void unlock(LockConfiguration lockConfiguration) {
        var bucketName = String.format("SHEDLOCK-%s", lockConfiguration.getName());
        log.debug("Unlocking for bucketName: {}", bucketName);
        var additionalSessionTtl = Duration.between(now(), lockConfiguration.getLockAtLeastUntil());
        if (!additionalSessionTtl.isNegative() && !additionalSessionTtl.isZero()) {
            log.debug("Lock will still be held for {}", additionalSessionTtl);
            scheduleUnlock(bucketName, additionalSessionTtl);
        } else {
            destroy(bucketName);
        }
    }

    private void scheduleUnlock(String bucketName, Duration unlockTime) {
        unlockScheduler.schedule(
                catchExceptions(() -> destroy(bucketName)), unlockTime.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void destroy(String bucketName) {
        log.debug("Destroying key in bucketName: {}", bucketName);
        try {
            connection.keyValue(bucketName).delete("LOCKED");
        } catch (Exception e) {
            throw new LockException("Can not remove key. " + e.getMessage());
        }
    }

    private Runnable catchExceptions(Runnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                log.warn("Exception while execution", t);
            }
        };
    }

    @Override
    public void close() {
        unlockScheduler.shutdown();
        try {
            if (!unlockScheduler.awaitTermination(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS)) {
                unlockScheduler.shutdownNow();
            }
        } catch (InterruptedException ignored) {
        }
    }
}
