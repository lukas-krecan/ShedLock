/**
 * Copyright 2009-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.core;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface LockingTaskExecutor {
    /**
     * Executes task if it's not already running.
     */
    void executeWithLock(@NotNull Runnable task, @NotNull LockConfiguration lockConfig);

    void executeWithLock(@NotNull Task task, @NotNull LockConfiguration lockConfig) throws Throwable;

    /**
     * Executes task.
     */
    @NotNull
    default <T> TaskResult<T> executeWithLock(@NotNull TaskWithResult<T> task, @NotNull LockConfiguration lockConfig) throws Throwable {
        throw new UnsupportedOperationException();
    }

    @FunctionalInterface
    interface Task {
        void call() throws Throwable;
    }

    @FunctionalInterface
    interface TaskWithResult<T> {
        T call() throws Throwable;
    }

    final class TaskResult<T> {
        private final boolean executed;
        private final T result;

        private TaskResult(boolean executed, @Nullable T result) {
            this.executed = executed;
            this.result = result;
        }

        public boolean wasExecuted() {
            return executed;
        }

        @Nullable
        public T getResult() {
            return result;
        }

        static <T> TaskResult<T> result(@Nullable T result) {
            return new TaskResult<>(true, result);
        }

        static <T> TaskResult<T> notExecuted() {
            return new TaskResult<>(false, null);
        }
    }
}
