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
package net.javacrumbs.shedlock.spring.aop;

import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

import java.util.concurrent.ScheduledExecutorService;

import static org.springframework.beans.factory.support.BeanDefinitionBuilder.rootBeanDefinition;
import static org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.DEFAULT_TASK_SCHEDULER_BEAN_NAME;

/**
 * Registers default TaskScheduler if none found.
 */
class RegisterDefaultTaskSchedulerPostProcessor implements BeanDefinitionRegistryPostProcessor, Ordered, BeanFactoryAware {
    private BeanFactory beanFactory;

    private static final Logger logger = LoggerFactory.getLogger(RegisterDefaultTaskSchedulerPostProcessor.class);

    @Override
    public void postProcessBeanDefinitionRegistry(@NonNull BeanDefinitionRegistry registry) throws BeansException {
        ListableBeanFactory listableBeanFactory = (ListableBeanFactory) this.beanFactory;
        if (BeanFactoryUtils.beanNamesForTypeIncludingAncestors(listableBeanFactory, TaskScheduler.class).length == 0) {
            String[] scheduledExecutorsBeanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(listableBeanFactory, ScheduledExecutorService.class);
            if (scheduledExecutorsBeanNames.length != 1) {
                logger.debug("Registering default TaskScheduler");
                registry.registerBeanDefinition(DEFAULT_TASK_SCHEDULER_BEAN_NAME, rootBeanDefinition(ConcurrentTaskScheduler.class).getBeanDefinition());
                if (scheduledExecutorsBeanNames.length != 0) {
                    logger.warn("Multiple ScheduledExecutorService found, do not know which one to use.");
                }
            } else {
                logger.debug("Registering default TaskScheduler with existing ScheduledExecutorService {}", scheduledExecutorsBeanNames[0]);
                registry.registerBeanDefinition(DEFAULT_TASK_SCHEDULER_BEAN_NAME,
                    rootBeanDefinition(ConcurrentTaskScheduler.class)
                        .addPropertyReference("scheduledExecutor", scheduledExecutorsBeanNames[0])
                        .getBeanDefinition()
                );
            }
        }
    }

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void setBeanFactory(@NonNull BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
}
