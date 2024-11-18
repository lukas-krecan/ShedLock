package net.javacrumbs.shedlock.provider.nats.jetstream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LockContentHandler {

    private static final Logger log = LoggerFactory.getLogger(LockContentHandler.class);

    private LockContentHandler() {}

    static byte[] writeContent(LockContent lockContent) {
        log.debug("write lock: {}", lockContent);
        return lockContent.lockAtLeastUntil().toString().getBytes();
    }
}
