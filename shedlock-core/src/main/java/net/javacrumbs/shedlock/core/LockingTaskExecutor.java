package net.javacrumbs.shedlock.core;

public interface LockingTaskExecutor {
    /**
     * Executes task if it's not already running.
     */
    void executeWithLock(Runnable task, LockConfiguration lockConfig);
}
