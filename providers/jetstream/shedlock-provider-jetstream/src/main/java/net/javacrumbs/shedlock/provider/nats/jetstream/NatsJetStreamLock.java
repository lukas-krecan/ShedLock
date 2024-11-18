package net.javacrumbs.shedlock.provider.nats.jetstream;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nats.client.Connection;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;

public final class NatsJetStreamLock extends AbstractSimpleLock {

  private final Logger log = LoggerFactory.getLogger(NatsJetStreamLock.class);

  private final Connection connection;

  protected NatsJetStreamLock(
      @NonNull Connection connection, @NonNull LockConfiguration lockConfiguration) {
    super(lockConfiguration);
    this.connection = connection;
  }

  @Override
  protected void doUnlock() {
    var bucketName = String.format("SHEDLOCK-%s", lockConfiguration.getName());
    log.debug("Unlocking for bucketName: {}", bucketName);
    var keepLockFor = getSecondUntil(lockConfiguration.getLockAtLeastUntil());
    if (keepLockFor <= 0) {
      try {
        log.debug("Calling delete on key");

        connection.keyValue(bucketName).delete("LOCKED");
      } catch (Exception e) {
        throw new LockException("Can not remove node. " + e.getMessage());
      }
    }
  }

  private static long getSecondUntil(Instant instant) {
    return Duration.between(ClockProvider.now(), instant).toMillis();
  }
}
