package net.javacrumbs.shedlock.core;

public interface SimpleLock {
    void unlock(Runnable task);
}
