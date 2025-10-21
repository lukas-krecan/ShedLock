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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;

public abstract class AbstractSchedulerConfig {
    private static final Logger logger = LoggerFactory.getLogger(AbstractSchedulerConfig.class);

    @Bean
    public LockProvider lockProvider() {
        LockProvider lockProvider = mock(LockProvider.class);
        when(lockProvider.lock(any())).thenReturn(Optional.of(mock(SimpleLock.class)));
        return lockProvider;
    }

    @Bean
    public ScheduledBean scheduledBean() {
        return new ScheduledBean();
    }

    // Does not work if scheduler configured in Configuration
    public static class ScheduledBean {
        @SchedulerLock(name = "taskName")
        @Scheduled(fixedRate = 10)
        public void run() {
            logger.info("Task executed");
        }
    }
}
