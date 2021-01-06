/**
 * Copyright 2009-2020 the original author or authors.
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
package net.javacrumbs.shedlock.spring.aop;

import net.javacrumbs.shedlock.core.DefaultLockManager;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.ExtendedLockConfigurationExtractor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Role;

@Configuration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
class SchedulerProxyLockConfiguration extends AbstractLockConfiguration {
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    SchedulerProxyScheduledLockAdvisor proxyScheduledLockAopBeanPostProcessor(
        @Lazy LockProvider lockProvider,
        @Lazy ExtendedLockConfigurationExtractor lockConfigurationExtractor
    ) {
        SchedulerProxyScheduledLockAdvisor advisor = new SchedulerProxyScheduledLockAdvisor(new DefaultLockManager(lockProvider, lockConfigurationExtractor));
        advisor.setOrder(getOrder());
        return advisor;
    }
}
