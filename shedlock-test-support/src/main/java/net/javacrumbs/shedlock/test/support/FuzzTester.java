/**
 * Copyright 2009-2017 the original author or authors.
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
package net.javacrumbs.shedlock.test.support;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Increments counter from several threads coordinating using lock provided under test.
 */
class FuzzTester {

    private static final int THREADS = 8;
    private static final int ITERATIONS = 100;

    private final LockProvider lockProvider;

    private int counter;
    private final LockConfiguration config = new LockConfiguration("lock", Instant.now().plus(5, ChronoUnit.MINUTES));

    FuzzTester(LockProvider lockProvider) {
        this.lockProvider = lockProvider;
    }

    void doFuzzTest() throws InterruptedException, ExecutionException {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);

        List<Callable<Void>> tasks = range(0, THREADS).mapToObj(i -> (Callable<Void>) this::task).collect(toList());
        waitForIt(executor.invokeAll(tasks));

        assertThat(counter).isEqualTo(THREADS * ITERATIONS);
    }

    private void waitForIt(List<Future<Void>> futures) throws InterruptedException, ExecutionException {
        for (Future<Void> f : futures) {
            f.get();
        }
    }

    private Void task() {
        for (int i = 0; i < ITERATIONS; ) {
            Optional<SimpleLock> lock = lockProvider.lock(config);
            if (lock.isPresent()) {
                int n = counter;
                sleep();
                counter = n + 1;
                i++;
                lock.get().unlock();
            }
        }
        return null;
    }

    private void sleep() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
