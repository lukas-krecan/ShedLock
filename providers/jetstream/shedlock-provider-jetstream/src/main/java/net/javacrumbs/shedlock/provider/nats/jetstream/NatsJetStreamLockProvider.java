package net.javacrumbs.shedlock.provider.nats.jetstream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.KeyValue;
import io.nats.client.api.KeyValueConfiguration;
import io.nats.client.api.StorageType;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lock Provider for NATS JetStream
 *
 * <p>
 * It uses a single bucket for all locks.
 *
 * @see <a href=
 *      "https://docs.nats.io/nats-concepts/jetstream/key-value-store">KV</a>
 */
public class NatsJetStreamLockProvider implements LockProvider {

    private static final Logger logger = LoggerFactory.getLogger(NatsJetStreamLockProvider.class);

    private static final String BUCKET_NAME = "shedlock-locks";

    private final KeyValue kv;

    public NatsJetStreamLockProvider(final Connection connection) {
        this(connection, BUCKET_NAME);
    }

    public NatsJetStreamLockProvider(final Connection connection, final String bucketName) {
        requireNonNull(connection, "connection can not be null");
        requireNonNull(bucketName, "bucketName can not be null");

        KeyValue kvInit;
        try {
            kvInit = connection.keyValue(bucketName);
        } catch (IOException e) {
            logger.debug("Failed to get bucket '{}'. Trying to create it.", bucketName, e);

            try {
                var config = KeyValueConfiguration.builder()
                        .name(bucketName)
                        .storageType(StorageType.Memory)
                        .build();

                connection.keyValueManagement().create(config);
                kvInit = connection.keyValue(bucketName);

            } catch (IOException | JetStreamApiException ex) {
                throw new LockException("Failed to create bucket", ex);
            }
        }
        this.kv = kvInit;
    }

    @Override
    public Optional<SimpleLock> lock(final LockConfiguration lockConfiguration) {
        try {
            var entry = kv.get(lockConfiguration.getName());

            if (entry == null) {
                return createLock(lockConfiguration);
            }

            var lockUntil = Instant.parse(new String(entry.getValue(), UTF_8));

            if (lockUntil.isAfter(ClockProvider.now())) {
                return Optional.empty();
            }

            return updateLock(lockConfiguration, entry.getRevision());

        } catch (IOException | JetStreamApiException e) {
            throw new LockException("Failed to get lock", e);
        }
    }

    private Optional<SimpleLock> createLock(final LockConfiguration lockConfiguration) {
        var lockUntil = lockConfiguration.getLockAtMostUntil();
        var value = lockUntil.toString().getBytes(UTF_8);

        try {
            kv.create(lockConfiguration.getName(), value);
            return Optional.of(new NatsJetStreamLock(this, lockConfiguration));

        } catch (IOException | JetStreamApiException e) {
            return Optional.empty(); // Should be caused by a race condition, another process was faster.
        }
    }

    private Optional<SimpleLock> updateLock(final LockConfiguration lockConfiguration, final long revision) {
        var lockUntil = lockConfiguration.getLockAtMostUntil();
        var value = lockUntil.toString().getBytes(UTF_8);

        try {
            kv.update(lockConfiguration.getName(), value, revision);
            return Optional.of(new NatsJetStreamLock(this, lockConfiguration));

        } catch (IOException | JetStreamApiException e) {
            return Optional.empty(); // Should be caused by a race condition, another process was faster.
        }
    }

    private void unlock(final LockConfiguration lockConfiguration) {
        var lockAtLeastUntil = lockConfiguration.getLockAtLeastUntil();
        var now = ClockProvider.now();

        try {
            var entry = kv.get(lockConfiguration.getName());

            if (entry == null) {
                return; // Already unlocked
            }

            var lockUntil = Instant.parse(new String(entry.getValue(), UTF_8));
            var lockAtMostUntil = lockConfiguration.getLockAtMostUntil();

            // If the lock has been updated by another process, we don't unlock.
            if (lockUntil.isAfter(lockAtMostUntil)) {
                return;
            }

            // If lockAtLeastUntil is in the future, we update the lock to expire at
            // lockAtLeastUntil instead of deleting it. This ensures the lock is held
            // for the minimum duration.
            if (lockAtLeastUntil.isAfter(now)) {
                var value = lockAtLeastUntil.toString().getBytes(UTF_8);
                kv.update(lockConfiguration.getName(), value, entry.getRevision());
                return;
            }

            kv.delete(lockConfiguration.getName());
        } catch (IOException | JetStreamApiException e) {
            throw new LockException("Failed to unlock", e);
        }
    }

    private static final class NatsJetStreamLock extends AbstractSimpleLock {

        private final NatsJetStreamLockProvider lockProvider;

        private NatsJetStreamLock(
                final NatsJetStreamLockProvider lockProvider, final LockConfiguration lockConfiguration) {
            super(lockConfiguration);
            this.lockProvider = lockProvider;
        }

        @Override
        public void doUnlock() {
            lockProvider.unlock(lockConfiguration);
        }
    }
}
