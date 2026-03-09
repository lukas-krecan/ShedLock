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

public class MicrometerLockingTaskExecutorListener implements LockingTaskExecutorListener {
    static final String LOCK_ATTEMPTS = "shedlock.lock.attempts";
    static final String LOCK_ACQUIRED = "shedlock.lock.acquired";
    static final String LOCK_NOT_ACQUIRED = "shedlock.lock.not_acquired";
    static final String EXECUTION = "shedlock.execution";
    static final String EXECUTION_DURATION = "shedlock.execution.duration";
    static final String EXECUTION_ACTIVE = "shedlock.execution.active";
    private static final String LOCK_NAME_TAG = "name";

    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeExecutions = new AtomicInteger();
    private final ConcurrentMap<String, Counter> attemptsCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> acquiredCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> notAcquiredCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> executionCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> executionTimers = new ConcurrentHashMap<>();

    public MicrometerLockingTaskExecutorListener(MeterRegistry meterRegistry) {
        this.meterRegistry = requireNonNull(meterRegistry);
        Gauge.builder(EXECUTION_ACTIVE, activeExecutions, AtomicInteger::get).register(meterRegistry);
    }

    @Override
    public void onLockAttempt(LockConfiguration lockConfig) {
        counterFor(attemptsCounters, LOCK_ATTEMPTS, lockConfig).increment();
    }

    @Override
    public void onLockAcquired(LockConfiguration lockConfig) {
        counterFor(acquiredCounters, LOCK_ACQUIRED, lockConfig).increment();
    }

    @Override
    public void onLockNotAcquired(LockConfiguration lockConfig) {
        counterFor(notAcquiredCounters, LOCK_NOT_ACQUIRED, lockConfig).increment();
    }

    @Override
    public void onTaskStarted(LockConfiguration lockConfig) {
        activeExecutions.incrementAndGet();
    }

    @Override
    public void onTaskFinished(LockConfiguration lockConfiguration, Duration executionTime) {
        activeExecutions.updateAndGet(current -> current > 0 ? current - 1 : 0);
        counterFor(executionCounters, EXECUTION, lockConfiguration).increment();
        timerFor(lockConfiguration).record(executionTime);
    }

    private Counter counterFor(
            ConcurrentMap<String, Counter> counters, String meterName, LockConfiguration lockConfiguration) {
        return counters.computeIfAbsent(lockConfiguration.getName(), ignored -> Counter.builder(meterName)
                .tag(LOCK_NAME_TAG, lockConfiguration.getName())
                .register(meterRegistry));
    }

    private Timer timerFor(LockConfiguration lockConfiguration) {
        return executionTimers.computeIfAbsent(lockConfiguration.getName(), ignored -> Timer.builder(EXECUTION_DURATION)
                .tag(LOCK_NAME_TAG, lockConfiguration.getName())
                .register(meterRegistry));
    }
}
