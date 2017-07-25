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
package net.javacrumbs.shedlock.test.boot;

import net.javacrumbs.shedlock.core.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Random;

@Component
public class ScheduledTasks {

    private static final Random sleepTimerRandom = new Random();

    @Scheduled(cron = "0/5 * * * * *")
    @SchedulerLock(name = "lockedTask")
    public void lockedTask() throws InterruptedException {
        final int milliSecSleep = sleepTimerRandom.nextInt(20000);
        System.out.println("Locked task : " + Instant.now().toString() + " (sleep = " + milliSecSleep + "ms)");
        Thread.sleep(milliSecSleep);
    }

    @Scheduled(cron = "0/5 * * * * *")
    public void notLockedTask() {
        System.out.println("Not locked task : " + Instant.now().toString());
    }

}