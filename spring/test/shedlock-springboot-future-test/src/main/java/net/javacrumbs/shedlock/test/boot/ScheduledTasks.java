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
package net.javacrumbs.shedlock.test.boot;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;

import static java.time.Duration.ZERO;
import static net.javacrumbs.shedlock.core.LockAssert.assertLocked;
import static net.javacrumbs.shedlock.core.LockExtender.extendActiveLock;

@Component
public class ScheduledTasks {
    @Scheduled(fixedRate = 100)
    @SchedulerLock(name = "reportCurrentTime", lockAtMostFor = "${lock.at.most.for}")
    public void reportCurrentTime() {
        assertLocked();
        extendActiveLock(Duration.ofMillis(5), ZERO);
        System.out.println(new Date());
    }
}
