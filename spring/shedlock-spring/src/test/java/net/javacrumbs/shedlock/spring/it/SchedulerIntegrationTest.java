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
package net.javacrumbs.shedlock.spring.it;

import static net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock.InterceptMode.PROXY_SCHEDULER;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = SchedulerIntegrationTest.AopSchedulerConfig.class)
@SuppressWarnings("removal")
public class SchedulerIntegrationTest extends AbstractSchedulerTest {

    @Configuration
    @EnableScheduling
    @EnableSchedulerLock(interceptMode = PROXY_SCHEDULER, defaultLockAtMostFor = "PT30S")
    public static class AopSchedulerConfig extends AbstractSchedulerConfig {}
}
