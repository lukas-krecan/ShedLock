/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.core;

import java.time.Duration;

/**
 * Listener for {@link LockingTaskExecutor} lifecycle events. Implementations receive callbacks at
 * each stage of lock acquisition and task execution.
 *
 * <p><b>Exception safety:</b> Implementations may throw without affecting lock management.
 * {@link DefaultLockingTaskExecutor} wraps every callback in a {@code safeEmit} guard that catches
 * and logs exceptions, ensuring listener failures never prevent a lock from being released.
 *
 * <p><b>Single-listener limitation:</b> {@link DefaultLockingTaskExecutor} accepts exactly one
 * listener. To combine multiple listeners (e.g. Micrometer metrics plus custom tracing), compose
 * them manually before passing to the executor.
 *
 * <p><b>Reentrant execution:</b> When a task re-enters the executor for the same lock on the same
 * thread, {@link #onTaskStarted} and {@link #onTaskFinished} are called for the inner execution,
 * but {@link #onLockAttempt} and {@link #onLockAcquired} are <em>not</em> — the lock is already
 * held. Implementations that track execution counts will therefore observe more {@code onTaskStarted}
 * events than {@code onLockAcquired} events in reentrant scenarios.
 */
public interface LockingTaskExecutorListener {

    /**
     * Called before attempting to acquire the lock. Not called for reentrant executions of the same
     * lock on the same thread.
     */
    default void onLockAttempt(LockConfiguration lockConfig) {}

    /**
     * Called after the lock is successfully acquired. Not called for reentrant executions of the same
     * lock on the same thread.
     */
    default void onLockAcquired(LockConfiguration lockConfig) {}

    /**
     * Called when the lock could not be acquired (another node holds it). The task will not be
     * executed.
     */
    default void onLockNotAcquired(LockConfiguration lockConfig) {}

    /**
     * Called immediately before the task body begins executing. Called for both initial and reentrant
     * executions.
     */
    default void onTaskStarted(LockConfiguration lockConfig) {}

    /**
     * Called after the task body finishes, whether it completed normally or threw an exception. Called
     * for both initial and reentrant executions.
     *
     * @param executionTime wall-clock duration of the task body (excluding lock acquisition time)
     */
    default void onTaskFinished(LockConfiguration lockConfiguration, Duration executionTime) {}

    /** No-op implementation, used as the default when no listener is configured. */
    LockingTaskExecutorListener NO_OP = new LockingTaskExecutorListener() {};
}
