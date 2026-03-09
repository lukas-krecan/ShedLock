package net.javacrumbs.shedlock.core;

import java.time.Duration;

public interface LockingTaskExecutorListener {

    void onLockAttempt(LockConfiguration lockConfig);

    void onLockAcquired(LockConfiguration lockConfig);

    void onLockNotAcquired(LockConfiguration lockConfig);

    void onTaskStarted(LockConfiguration lockConfig);

    void onTaskFinished(LockConfiguration lockConfiguration, Duration executionTime);

    LockingTaskExecutorListener NO_OP = new LockingTaskExecutorListener() {
        @Override
        public void onLockAttempt(LockConfiguration lockConfig) {}

        @Override
        public void onLockAcquired(LockConfiguration lockConfig) {}

        @Override
        public void onLockNotAcquired(LockConfiguration lockConfig) {}

        @Override
        public void onTaskStarted(LockConfiguration lockConfig) {}

        @Override
        public void onTaskFinished(LockConfiguration lockConfiguration, Duration executionTime) {}
    };
}
