/**
 * Copyright 2009-2018 the original author or authors.
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
package net.javacrumbs.shedlock.spring.wrapper;

import net.javacrumbs.shedlock.core.DefaultLockManager;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.LockableTaskScheduler;
import net.javacrumbs.shedlock.spring.internal.ScheduledMethodRunnableSpringLockConfigurationExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.StringValueResolver;

import java.time.Duration;

public class TaskSchedulerWrapperBeanPostProcessor implements BeanPostProcessor, EmbeddedValueResolverAware, BeanFactoryAware {
    private static final Logger logger = LoggerFactory.getLogger(TaskSchedulerWrapperBeanPostProcessor.class);

    private final String defaultLockAtMostFor;
    private final String defaultLockAtLeastFor;
    private BeanFactory beanFactory;
    private StringValueResolver resolver;
    private LockProvider lockProvider;

    public TaskSchedulerWrapperBeanPostProcessor(String defaultLockAtMostFor, String defaultLockAtLeastFor) {
        this.defaultLockAtMostFor = defaultLockAtMostFor;
        this.defaultLockAtLeastFor = defaultLockAtLeastFor;
    }

    /**
     * Set the StringValueResolver to use for resolving embedded definition values.
     */
    @Override
    public void setEmbeddedValueResolver(StringValueResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }


    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof TaskScheduler) {
            if (lockProvider == null) {
                lockProvider = beanFactory.getBean(LockProvider.class);
            }
            logger.debug("Wrapping TaskScheduler {} in LockableTaskScheduler.");
            ScheduledMethodRunnableSpringLockConfigurationExtractor lockConfigurationExtractor = new ScheduledMethodRunnableSpringLockConfigurationExtractor(
                toDuration(defaultLockAtMostFor),
                toDuration(defaultLockAtLeastFor),
                resolver
            );
            return new LockableTaskScheduler((TaskScheduler) bean, new DefaultLockManager(lockProvider, lockConfigurationExtractor));
        } else {
            return bean;
        }
    }

    private Duration toDuration(String string) {
        return Duration.parse(resolver.resolveStringValue(string));
    }

    public LockProvider getLockProvider() {
        return lockProvider;
    }

    /**
     * Support for manually setting lockProvider
     */
    public void setLockProvider(LockProvider lockProvider) {
        this.lockProvider = lockProvider;
    }
}
