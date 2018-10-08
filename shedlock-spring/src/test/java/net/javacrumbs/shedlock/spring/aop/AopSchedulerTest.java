/**
 * Copyright 2009-2017 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.spring.aop;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static net.javacrumbs.shedlock.spring.aop.AopTest.hasName;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;


@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = AopConfig.class)
public class AopSchedulerTest {
    private static final Logger logger = LoggerFactory.getLogger(AopSchedulerTest.class);

    @Autowired
    private LockProvider lockProvider;

    @Test
    public void shouldCallLockProvider() {
        await().untilAsserted(() -> verify(lockProvider, atLeastOnce()).lock(hasName("taskName")));
    }


    @SchedulerLock(name = "taskName")
    @Scheduled(fixedRate = 10)
    public void run() {
        logger.info("Task executed");
    }
}
