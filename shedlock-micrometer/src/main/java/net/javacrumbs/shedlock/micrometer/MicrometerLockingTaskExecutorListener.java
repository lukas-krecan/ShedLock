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

import static java.util.Objects.requireNonNull;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutorListener;

/**
 * {@link LockingTaskExecutorListener} that records ShedLock execution events as Micrometer metrics.
 *
 * <p>The following meters are registered, all tagged with {@code lock.name}:
 *
 * <ul>
 *   <li>{@code shedlock.lock.attempts} (Counter) — total lock acquisition attempts
 *   <li>{@code shedlock.lock.acquired} (Counter) — successful lock acquisitions
 *   <li>{@code shedlock.lock.not.acquired} (Counter) — failed lock acquisitions (lock held elsewhere)
 *   <li>{@code shedlock.execution.duration} (Timer) — task execution time; {@code timer.count()}
 *       gives total completed executions
 *   <li>{@code shedlock.execution.active} (Gauge) — number of currently executing tasks
 * </ul>
 *
 * <p>All meters are registered lazily on first use. To pre-register meters for known lock names
 * (so dashboards show zero rather than missing data before the first execution), call
 * {@link #registerMetricsFor(String...)}.
 */
public class MicrometerLockingTaskExecutorListener implements LockingTaskExecutorListener {
    static final String LOCK_ATTEMPTS = "shedlock.lock.attempts";
    static final String LOCK_ACQUIRED = "shedlock.lock.acquired";
    static final String LOCK_NOT_ACQUIRED = "shedlock.lock.not.acquired";
    static final String EXECUTION_DURATION = "shedlock.execution.duration";
    static final String EXECUTION_ACTIVE = "shedlock.execution.active";
    static final String LOCK_NAME_TAG = "lock.name";

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Counter> attemptsCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> acquiredCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> notAcquiredCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> executionTimers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> activeCounters = new ConcurrentHashMap<>();

    public MicrometerLockingTaskExecutorListener(MeterRegistry meterRegistry) {
        this.meterRegistry = requireNonNull(meterRegistry);
    }

    /**
     * Pre-registers all meters for the given lock names. Useful for ensuring metrics appear in
     * dashboards immediately on startup, before any lock contention or execution has occurred.
     *
     * @param lockNames the lock names to pre-register metrics for
     */
    public void registerMetricsFor(String... lockNames) {
        for (String lockName : lockNames) {
            attemptsCounters.computeIfAbsent(lockName, name -> buildCounter(LOCK_ATTEMPTS, name));
            acquiredCounters.computeIfAbsent(lockName, name -> buildCounter(LOCK_ACQUIRED, name));
            notAcquiredCounters.computeIfAbsent(lockName, name -> buildCounter(LOCK_NOT_ACQUIRED, name));
            executionTimers.computeIfAbsent(lockName, this::buildTimer);
            activeCounters.computeIfAbsent(lockName, this::buildActiveCounter);
        }
    }

    @Override
    public void onLockAttempt(LockConfiguration lockConfig) {
        attemptsCounters
                .computeIfAbsent(lockConfig.getName(), name -> buildCounter(LOCK_ATTEMPTS, name))
                .increment();
    }

    @Override
    public void onLockAcquired(LockConfiguration lockConfig) {
        acquiredCounters
                .computeIfAbsent(lockConfig.getName(), name -> buildCounter(LOCK_ACQUIRED, name))
                .increment();
    }

    @Override
    public void onLockNotAcquired(LockConfiguration lockConfig) {
        notAcquiredCounters
                .computeIfAbsent(lockConfig.getName(), name -> buildCounter(LOCK_NOT_ACQUIRED, name))
                .increment();
    }

    @Override
    public void onTaskStarted(LockConfiguration lockConfig) {
        activeCounters
                .computeIfAbsent(lockConfig.getName(), this::buildActiveCounter)
                .incrementAndGet();
    }

    @Override
    public void onTaskFinished(LockConfiguration lockConfig, Duration executionTime) {
        activeCounters
                .computeIfAbsent(lockConfig.getName(), this::buildActiveCounter)
                .updateAndGet(current -> current > 0 ? current - 1 : 0);
        executionTimers.computeIfAbsent(lockConfig.getName(), this::buildTimer).record(executionTime);
    }

    private Counter buildCounter(String metricName, String lockName) {
        return Counter.builder(metricName).tag(LOCK_NAME_TAG, lockName).register(meterRegistry);
    }

    private Timer buildTimer(String lockName) {
        return Timer.builder(EXECUTION_DURATION).tag(LOCK_NAME_TAG, lockName).register(meterRegistry);
    }

    private AtomicInteger buildActiveCounter(String lockName) {
        AtomicInteger counter = new AtomicInteger();
        Gauge.builder(EXECUTION_ACTIVE, counter, AtomicInteger::get)
                .tag(LOCK_NAME_TAG, lockName)
                .register(meterRegistry);
        return counter;
    }
}
