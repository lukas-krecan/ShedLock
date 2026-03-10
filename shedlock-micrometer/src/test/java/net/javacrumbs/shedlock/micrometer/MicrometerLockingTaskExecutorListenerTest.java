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
package net.javacrumbs.shedlock.micrometer;

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Optional;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;

class MicrometerLockingTaskExecutorListenerTest {
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final MicrometerLockingTaskExecutorListener listener =
            new MicrometerLockingTaskExecutorListener(meterRegistry);
    private final LockConfiguration lockConfiguration =
            new LockConfiguration(now(), "test", Duration.ofSeconds(10), Duration.ZERO);
    private final LockConfiguration otherLockConfiguration =
            new LockConfiguration(now(), "other", Duration.ofSeconds(10), Duration.ZERO);

    @Test
    void shouldRecordSuccessfulExecutionUsingExecutor() {
        LockProvider lockProvider = lockConfiguration -> Optional.of(new TestSimpleLock());
        DefaultLockingTaskExecutor executor = new DefaultLockingTaskExecutor(lockProvider, listener);

        executor.executeWithLock((Runnable) () -> {}, lockConfiguration);

        assertThat(counter(MicrometerLockingTaskExecutorListener.LOCK_ATTEMPTS, "test"))
                .isEqualTo(1.0);
        assertThat(counter(MicrometerLockingTaskExecutorListener.LOCK_ACQUIRED, "test"))
                .isEqualTo(1.0);
        assertThat(counter(MicrometerLockingTaskExecutorListener.LOCK_NOT_ACQUIRED, "test"))
                .isZero();
        assertThat(timerCount("test")).isEqualTo(1L);
        assertThat(activeGauge("test")).isZero();
    }

    @Test
    void shouldRecordNotAcquiredLockUsingExecutor() {
        LockProvider lockProvider = lockConfiguration -> Optional.empty();
        DefaultLockingTaskExecutor executor = new DefaultLockingTaskExecutor(lockProvider, listener);

        executor.executeWithLock((Runnable) () -> {}, lockConfiguration);

        assertThat(counter(MicrometerLockingTaskExecutorListener.LOCK_ATTEMPTS, "test"))
                .isEqualTo(1.0);
        assertThat(counter(MicrometerLockingTaskExecutorListener.LOCK_ACQUIRED, "test"))
                .isZero();
        assertThat(counter(MicrometerLockingTaskExecutorListener.LOCK_NOT_ACQUIRED, "test"))
                .isEqualTo(1.0);
        assertThat(timerCount("test")).isZero();
        assertThat(activeGauge("test")).isZero();
    }

    @Test
    void shouldTrackActiveExecutions() {
        listener.onTaskStarted(lockConfiguration);

        assertThat(activeGauge("test")).isEqualTo(1.0);

        listener.onTaskFinished(lockConfiguration, Duration.ofMillis(5));

        assertThat(activeGauge("test")).isZero();
        assertThat(timerCount("test")).isEqualTo(1L);
    }

    @Test
    void shouldDecrementActiveGaugeWhenTaskThrows() {
        LockProvider lockProvider = lockConfiguration -> Optional.of(new TestSimpleLock());
        DefaultLockingTaskExecutor executor = new DefaultLockingTaskExecutor(lockProvider, listener);

        assertThatThrownBy(() -> executor.executeWithLock(
                        (Runnable) () -> {
                            throw new RuntimeException("task failed");
                        },
                        lockConfiguration))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("task failed");

        assertThat(activeGauge("test")).isZero();
        assertThat(timerCount("test")).isEqualTo(1L);
    }

    @Test
    void shouldSeparateMetricsByLockName() {
        listener.onLockAttempt(lockConfiguration);
        listener.onLockAttempt(otherLockConfiguration);
        listener.onLockAcquired(otherLockConfiguration);
        listener.onTaskStarted(otherLockConfiguration);
        listener.onTaskFinished(otherLockConfiguration, Duration.ofMillis(10));

        assertThat(counter(MicrometerLockingTaskExecutorListener.LOCK_ATTEMPTS, "test"))
                .isEqualTo(1.0);
        assertThat(counter(MicrometerLockingTaskExecutorListener.LOCK_ATTEMPTS, "other"))
                .isEqualTo(1.0);
        assertThat(counter(MicrometerLockingTaskExecutorListener.LOCK_ACQUIRED, "test"))
                .isZero();
        assertThat(counter(MicrometerLockingTaskExecutorListener.LOCK_ACQUIRED, "other"))
                .isEqualTo(1.0);
        assertThat(timerCount("test")).isZero();
        assertThat(timerCount("other")).isEqualTo(1L);
    }

    private double counter(String meterName, String lockName) {
        try {
            return meterRegistry.get(meterName).tag("name", lockName).counter().count();
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private long timerCount(String lockName) {
        try {
            return meterRegistry
                    .get(MicrometerLockingTaskExecutorListener.EXECUTION_DURATION)
                    .tag("name", lockName)
                    .timer()
                    .count();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private double activeGauge(String lockName) {
        try {
            return meterRegistry
                    .get(MicrometerLockingTaskExecutorListener.EXECUTION_ACTIVE)
                    .tag("name", lockName)
                    .gauge()
                    .value();
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private static class TestSimpleLock implements SimpleLock {
        @Override
        public void unlock() {}
    }
}
