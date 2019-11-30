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
package net.javacrumbs.micronaut.test;

import io.micronaut.scheduling.annotation.Scheduled;
import net.javacrumbs.shedlock.micronaut.SchedulerLock;

import javax.inject.Singleton;
import java.util.Date;

import static net.javacrumbs.shedlock.core.LockAssert.assertLocked;

@Singleton
public class ScheduledTasks {

    @Scheduled(fixedDelay = "1s")
    @SchedulerLock(name = "reportCurrentTime")
    public void reportCurrentTime() {
        assertLocked();
        System.out.println(new Date());
    }
}