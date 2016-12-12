package net.javacrumbs.shedlock.core;

import java.util.Optional;

/**
 * Provides lock implementation.
 */
public interface LockProvider {
    Optional<SimpleLock> lock(LockConfiguration lockConfiguration);
}
