package net.javacrumbs.shedlock.core;

import java.time.Duration;

public interface LockingTaskExecutorListener {

    default void onLockAttempt(LockConfiguration lockConfig) {}

    default void onLockAcquired(LockConfiguration lockConfig) {}

    default void onLockNotAcquired(LockConfiguration lockConfig) {}

    default void onTaskStarted(LockConfiguration lockConfig) {}

    default void onTaskFinished(LockConfiguration lockConfiguration, Duration executionTime) {}

    LockingTaskExecutorListener NO_OP = new LockingTaskExecutorListener() {};
}
