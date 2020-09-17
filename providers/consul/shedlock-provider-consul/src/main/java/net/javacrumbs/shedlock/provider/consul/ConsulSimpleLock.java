package net.javacrumbs.shedlock.provider.consul;

import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.LockConfiguration;

class ConsulSimpleLock extends AbstractSimpleLock {
    private final ConsulLockProvider consulLockProvider;
    private final String sessionId;

    public ConsulSimpleLock(LockConfiguration lockConfiguration,
                            ConsulLockProvider consulLockProvider,
                            String sessionId) {
        super(lockConfiguration);
        this.consulLockProvider = consulLockProvider;
        this.sessionId = sessionId;
    }

    @Override
    protected void doUnlock() {
        consulLockProvider.unlock(sessionId, lockConfiguration);
    }
}
