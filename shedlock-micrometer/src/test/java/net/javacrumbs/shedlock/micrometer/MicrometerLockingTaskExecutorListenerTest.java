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
import static net.javacrumbs.shedlock.micrometer.MicrometerLockingTaskExecutorListener.EXECUTION_ACTIVE;
import static net.javacrumbs.shedlock.micrometer.MicrometerLockingTaskExecutorListener.EXECUTION_DURATION;
import static net.javacrumbs.shedlock.micrometer.MicrometerLockingTaskExecutorListener.LOCK_ACQUIRED;
import static net.javacrumbs.shedlock.micrometer.MicrometerLockingTaskExecutorListener.LOCK_ATTEMPTS;
import static net.javacrumbs.shedlock.micrometer.MicrometerLockingTaskExecutorListener.LOCK_NAME_TAG;
import static net.javacrumbs.shedlock.micrometer.MicrometerLockingTaskExecutorListener.LOCK_NOT_ACQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Optional;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
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

        assertThat(counter(LOCK_ATTEMPTS, "test")).isEqualTo(1.0);
        assertThat(counter(LOCK_ACQUIRED, "test")).isEqualTo(1.0);
        assertThat(meterRegistry
                        .find(LOCK_NOT_ACQUIRED)
                        .tag(LOCK_NAME_TAG, "test")
                        .counter())
                .isNull();
        assertThat(timerCount("test")).isEqualTo(1L);
        assertThat(activeGauge("test")).isZero();
    }

    @Test
    void shouldRecordNotAcquiredLockUsingExecutor() {
        LockProvider lockProvider = lockConfiguration -> Optional.empty();
        DefaultLockingTaskExecutor executor = new DefaultLockingTaskExecutor(lockProvider, listener);

        executor.executeWithLock((Runnable) () -> {}, lockConfiguration);

        assertThat(counter(LOCK_ATTEMPTS, "test")).isEqualTo(1.0);
        assertThat(meterRegistry.find(LOCK_ACQUIRED).tag(LOCK_NAME_TAG, "test").counter())
                .isNull();
        assertThat(counter(LOCK_NOT_ACQUIRED, "test")).isEqualTo(1.0);
        assertThat(meterRegistry
                        .find(EXECUTION_DURATION)
                        .tag(LOCK_NAME_TAG, "test")
                        .timer())
                .isNull();
        assertThat(meterRegistry
                        .find(EXECUTION_ACTIVE)
                        .tag(LOCK_NAME_TAG, "test")
                        .gauge())
                .isNull();
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

        assertThat(counter(LOCK_ATTEMPTS, "test")).isEqualTo(1.0);
        assertThat(counter(LOCK_ATTEMPTS, "other")).isEqualTo(1.0);
        assertThat(meterRegistry.find(LOCK_ACQUIRED).tag(LOCK_NAME_TAG, "test").counter())
                .isNull();
        assertThat(counter(LOCK_ACQUIRED, "other")).isEqualTo(1.0);
        assertThat(meterRegistry
                        .find(EXECUTION_DURATION)
                        .tag(LOCK_NAME_TAG, "test")
                        .timer())
                .isNull();
        assertThat(timerCount("other")).isEqualTo(1L);
    }

    @Test
    void shouldPreRegisterMetersForKnownLockNames() {
        listener.registerMetricsFor("my-job", "other-job");

        assertThat(counter(LOCK_ATTEMPTS, "my-job")).isZero();
        assertThat(counter(LOCK_ACQUIRED, "my-job")).isZero();
        assertThat(counter(LOCK_NOT_ACQUIRED, "my-job")).isZero();
        assertThat(timerCount("my-job")).isZero();
        assertThat(activeGauge("my-job")).isZero();

        assertThat(counter(LOCK_ATTEMPTS, "other-job")).isZero();
        assertThat(counter(LOCK_ACQUIRED, "other-job")).isZero();
        assertThat(counter(LOCK_NOT_ACQUIRED, "other-job")).isZero();
        assertThat(timerCount("other-job")).isZero();
        assertThat(activeGauge("other-job")).isZero();
    }

    @Test
    void registerMetricsForShouldBeIdempotent() {
        listener.registerMetricsFor("my-job");
        listener.onLockAttempt(new LockConfiguration(now(), "my-job", Duration.ofSeconds(10), Duration.ZERO));

        listener.registerMetricsFor("my-job");

        assertThat(counter(LOCK_ATTEMPTS, "my-job")).isEqualTo(1.0);
        assertThat(meterRegistry
                        .find(LOCK_ATTEMPTS)
                        .tag(LOCK_NAME_TAG, "my-job")
                        .counters())
                .hasSize(1);
    }

    private double counter(String meterName, String lockName) {
        return meterRegistry
                .get(meterName)
                .tag(LOCK_NAME_TAG, lockName)
                .counter()
                .count();
    }

    private long timerCount(String lockName) {
        return meterRegistry
                .get(EXECUTION_DURATION)
                .tag(LOCK_NAME_TAG, lockName)
                .timer()
                .count();
    }

    private double activeGauge(String lockName) {
        return meterRegistry
                .get(EXECUTION_ACTIVE)
                .tag(LOCK_NAME_TAG, lockName)
                .gauge()
                .value();
    }

    private static class TestSimpleLock implements net.javacrumbs.shedlock.core.SimpleLock {
        @Override
        public void unlock() {}
    }
}
