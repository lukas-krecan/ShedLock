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
package net.javacrumbs.shedlock.spring.annotation;

import net.javacrumbs.shedlock.core.DefaultLockManager;
import net.javacrumbs.shedlock.core.LockManager;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.LockableTaskScheduler;
import net.javacrumbs.shedlock.spring.internal.ScheduledMethodSpringLockConfigurationExtractor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.util.StringValueResolver;

import java.time.Duration;

class AnnotationBasedLockableTaskSchedulerFactoryBean extends AbstractFactoryBean<LockableTaskScheduler> implements EmbeddedValueResolverAware {

    private final String defaultLockAtMostFor;
    private final String defaultLockAtLeastFor;

    private TaskScheduler taskScheduler;
    private StringValueResolver resolver;

    private LockProvider lockProvider;
    private BeanFactory beanFactory;


    AnnotationBasedLockableTaskSchedulerFactoryBean(String defaultLockAtMostFor, String defaultLockAtLeastFor) {
        this.defaultLockAtMostFor = defaultLockAtMostFor;
        this.defaultLockAtLeastFor = defaultLockAtLeastFor;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (lockProvider == null) {
            lockProvider = beanFactory.getBean(LockProvider.class);
        }
        if (taskScheduler == null) {
            taskScheduler = beanFactory.getBean("taskScheduler", TaskScheduler.class);
        }
        super.afterPropertiesSet();
    }

    @Override
    protected SelfRegisteringLockableTaskScheduler createInstance() {
        ScheduledMethodSpringLockConfigurationExtractor configurationExtractor
            = new ScheduledMethodSpringLockConfigurationExtractor(toDuration(defaultLockAtMostFor), toDuration(defaultLockAtLeastFor), resolver);
        return new SelfRegisteringLockableTaskScheduler(
            taskScheduler,
            new DefaultLockManager(lockProvider, configurationExtractor)
        );
    }

    private Duration toDuration(String string) {
        return Duration.parse(resolver.resolveStringValue(string));
    }

    @Override
    public void setEmbeddedValueResolver(StringValueResolver resolver) {
        this.resolver = resolver;
    }

    public void setTaskScheduler(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    public void setLockProvider(LockProvider lockProvider) {
        this.lockProvider = lockProvider;
    }

    @Override
    public Class<?> getObjectType() {
        return SelfRegisteringLockableTaskScheduler.class;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        super.setBeanFactory(beanFactory);
        this.beanFactory = beanFactory;
    }

    static class SelfRegisteringLockableTaskScheduler extends LockableTaskScheduler implements SchedulingConfigurer {
        SelfRegisteringLockableTaskScheduler(TaskScheduler taskScheduler, LockManager lockManager) {
            super(taskScheduler, lockManager);
        }

        @Override
        public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
            taskRegistrar.setTaskScheduler(this);
        }
    }
}
