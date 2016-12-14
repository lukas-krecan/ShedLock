package net.javacrumbs.shedlock.core;

import java.util.Optional;

/**
 * Provides lock implementation.
 */
public interface LockProvider {

    /**
     * @return If empty optional has been returned, lock could not be acquired
     */
    Optional<SimpleLock> lock(LockConfiguration lockConfiguration);
}
