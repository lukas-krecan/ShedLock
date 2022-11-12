/**
 * Copyright 2009 the original author or authors.
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
package net.javacrumbs.shedlock.quarkus.test;


import net.javacrumbs.shedlock.cdi.SchedulerLock;
import net.javacrumbs.shedlock.core.LockProvider;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;
import java.io.IOException;

import static net.javacrumbs.shedlock.core.LockAssert.assertLocked;
import static org.mockito.Mockito.mock;

public class QuarkusConfig {

    @Produces
    @Singleton
    public LockProvider lockProvider() {
        return mock(LockProvider.class);
    }


    @ApplicationScoped
    static class TestBean {

        public void noAnnotation() {
            assertLocked();
        }

        @SchedulerLock(name = "normal")
        public void normal() {
            assertLocked();
        }

        @SchedulerLock(name = "runtimeException", lockAtMostFor = "PT0.1s")
        public Void throwsRuntimeException() {
            throw new RuntimeException();
        }

        @SchedulerLock(name = "exception")
        public void throwsException() throws Exception {
            throw new IOException();
        }

        @SchedulerLock(name = "returnsValue")
        public int returnsValue() {
            return 0;
        }

        @SchedulerLock(name = "${property.value}", lockAtLeastFor = "${property.lock-at-least-for}")
        public void property() {

        }
    }


    interface AnotherTestBean {
        void runManually();
    }

    @ApplicationScoped
    static class AnotherTestBeanImpl implements AnotherTestBean {

        @Override
        @SchedulerLock(name = "classAnnotation")
        public void runManually() {

        }
    }
}
