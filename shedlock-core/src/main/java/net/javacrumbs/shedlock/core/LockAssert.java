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

import java.util.Deque;
import java.util.LinkedList;

/**
 * Asserts lock presence. The Spring ecosystem is so complicated, so one can not
 * be sure that the lock is applied. This class makes sure that the task is
 * indeed locked.
 *
 * <p>
 * If you use AOP with Kotlin, it does not have to work due to final methods, if
 * you use TaskExecutor wrapper, it can be broken by Sleuth,.
 */
public final class LockAssert {
    // using null initial value so new LinkedList is not created every time we call
    // alreadyLockedBy
    private static final ThreadLocal<Deque<String>> activeLocksTL = ThreadLocal.withInitial(() -> null);

    private LockAssert() {}

    static void startLock(String name) {
        activeLocks().add(name);
    }

    static boolean alreadyLockedBy(String name) {
        Deque<String> activeLocks = activeLocksTL.get();
        return activeLocks != null && activeLocks.contains(name);
    }

    static void endLock() {
        Deque<String> activeLocks = activeLocks();
        activeLocks.removeLast();
        if (activeLocks.isEmpty()) {
            activeLocksTL.remove();
        }
    }

    private static Deque<String> activeLocks() {
        if (activeLocksTL.get() == null) {
            activeLocksTL.set(new LinkedList<>());
        }
        return activeLocksTL.get();
    }

    /** Throws an exception if the lock is not present. */
    public static void assertLocked() {
        Deque<String> activeLocks = activeLocksTL.get();
        if (activeLocks == null || activeLocks.isEmpty()) {
            throw new IllegalStateException("The task is not locked.");
        }
    }

    public static class TestHelper {

        private static final String TEST_LOCK_NAME = "net.javacrumbs.shedlock.core.test-lock";

        /**
         * If pass is set to true, all LockAssert.assertLocked calls in current thread
         * will pass. To be used in unit tests only <code>
         * LockAssert.TestHelper.makeAllAssertsPass(true)
         * </code>
         */
        public static void makeAllAssertsPass(boolean pass) {
            if (pass) {
                LockAssert.startLock(TEST_LOCK_NAME);
            } else {
                if (LockAssert.alreadyLockedBy(TEST_LOCK_NAME)) {
                    LockAssert.endLock();
                }
            }
        }
    }
}
