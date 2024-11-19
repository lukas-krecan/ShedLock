package net.javacrumbs.shedlock.provider.nats.jetstream;

import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.annotation.NonNull;

public final class NatsJetStreamLock extends AbstractSimpleLock {

    private final NatsJetStreamLockProvider natsJetStreamLockProvider;

    protected NatsJetStreamLock(
            @NonNull LockConfiguration lockConfiguration,
            @NonNull NatsJetStreamLockProvider natsJetStreamLockProvider) {
        super(lockConfiguration);
        this.natsJetStreamLockProvider = natsJetStreamLockProvider;
    }

    @Override
    protected void doUnlock() {
        natsJetStreamLockProvider.unlock(lockConfiguration);
    }
}
