/**
 * Copyright 2009-2018 the original author or authors.
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
package net.javacrumbs.shedlock.spring.it;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.TestUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static net.javacrumbs.shedlock.spring.TestUtils.hasParams;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;


@RunWith(SpringJUnit4ClassRunner.class)
public abstract class AbstractSchedulerTest {

    @Autowired
    private LockProvider lockProvider;

    @Test
    public void shouldCallLockProvider() {
        await().untilAsserted(() -> verify(lockProvider, atLeastOnce()).lock(hasParams("taskName", 30_000, 0)));
    }
}
