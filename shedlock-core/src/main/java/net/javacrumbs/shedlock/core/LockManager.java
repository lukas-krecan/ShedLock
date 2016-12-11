package net.javacrumbs.shedlock.core;

/**
 * Executes task if not locked.
 */
public interface LockManager {
    void executeIfNotLocked(Runnable task);
}
