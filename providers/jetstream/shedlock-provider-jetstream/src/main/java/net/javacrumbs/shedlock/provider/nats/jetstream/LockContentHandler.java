package net.javacrumbs.shedlock.provider.nats.jetstream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.javacrumbs.shedlock.support.annotation.NonNull;

public class LockContentHandler {

  private static final Logger log = LoggerFactory.getLogger(LockContentHandler.class);

  private LockContentHandler() {}

  public static byte[] writeContent(@NonNull LockContent lockContent) {
    log.debug("write lock: {}", lockContent);
    return lockContent.lockAtLeastUntil().toString().getBytes();
  }
}
