package net.javacrumbs.shedlock.provider.nats.jetstream;

import static net.javacrumbs.shedlock.support.Utils.getHostname;
import static net.javacrumbs.shedlock.support.Utils.toIsoString;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import io.nats.client.Connection;
import io.nats.client.api.KeyValueConfiguration;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;

/**
 * Lock Provider for NATS JetStream
 *
 * @see <a href="https://docs.nats.io/nats-concepts/jetstream">JetStream</a>
 */
public class NatsJetStreamLockProvider implements LockProvider {

    /** KEY PREFIX */
    private static final String KEY_PREFIX = "shedlock";

    private final Connection connection;

    /**
     * Create NatsJetStreamLockProvider
     *
     * @param connection
     *            io.nats.client.Connection
     */
    public NatsJetStreamLockProvider(@NonNull Connection connection) {
        this.connection = connection;
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        var expireTime = getSecondUntil(lockConfiguration.getLockAtMostUntil());
        var key = buildKey(lockConfiguration.getName());

        try {
            var kvm = connection.keyValueManagement();
            kvm.create(KeyValueConfiguration.builder().name("shedlock").build());

            var kv = connection.keyValue("shedlock");
            kv.put(key, key.getBytes());
        } catch(Exception e) {
            return Optional.empty();
        }

        return Optional.of(new NatsJetStreamLock(key, connection, lockConfiguration));
    }

    private static long getSecondUntil(Instant instant) {
        var millis = Duration.between(ClockProvider.now(), instant).toMillis();
        return millis / 1000;
    }

    static String buildKey(String lockName) {
        return String.format("%s_%s", KEY_PREFIX, lockName);
    }

    private static String buildValue() {
        return String.format("ADDED:%s@%s", toIsoString(ClockProvider.now()), getHostname());
    }

    private static final class NatsJetStreamLock extends AbstractSimpleLock {

        private final String key;

        private final Connection connection;

        private NatsJetStreamLock(
                @NonNull String key, @NonNull Connection connection, @NonNull LockConfiguration lockConfiguration) {
            super(lockConfiguration);
            this.key = key;
            this.connection = connection;
        }

        @Override
        protected void doUnlock() {
            var keepLockFor = getSecondUntil(lockConfiguration.getLockAtLeastUntil());
            if (keepLockFor <= 0) {
                try {
                    connection.keyValue("shedlock").delete(key);
                } catch(Exception e) {
                    throw new LockException("Can not remove node. " + e.getMessage());
                }
            } else {
                try {
                    connection.keyValue("shedlock").update(key, KEY_PREFIX, 1);
                } catch(Exception e) {
                    throw new LockException("Can not replace node. " + e.getMessage());
                }
            }
        }
    }
}
