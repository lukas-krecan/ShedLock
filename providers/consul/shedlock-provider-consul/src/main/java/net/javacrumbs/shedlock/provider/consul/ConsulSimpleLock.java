package net.javacrumbs.shedlock.provider.consul;

import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;

import java.time.Instant;
import java.util.Optional;

public class ConsulSimpleLock extends AbstractSimpleLock {
    private final ConsulTtlLockProvider consulTtlLockProvider;
    private final String sessionId;
    private final Instant createdAt;

    public ConsulSimpleLock(LockConfiguration lockConfiguration,
                            ConsulTtlLockProvider consulTtlLockProvider,
                            String sessionId,
                            Instant createdAt) {
        super(lockConfiguration);
        this.consulTtlLockProvider = consulTtlLockProvider;
        this.sessionId = sessionId;
        this.createdAt = createdAt;
    }

    @Override
    protected void doUnlock() {
        consulTtlLockProvider.unlock(sessionId, lockConfiguration);
    }

    @Override
    protected Optional<SimpleLock> doExtend(LockConfiguration newConfiguration) {
        //TODO check with Lukas whether extension will be supported or not, I don't see any extend() usages in code
        if (lockConfiguration.getLockAtMostUntil().isAfter(ClockProvider.now())) {
            return Optional.empty();
        }
        return consulTtlLockProvider.extend(sessionId, newConfiguration);
    }
}
