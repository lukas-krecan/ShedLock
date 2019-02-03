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

import net.javacrumbs.shedlock.spring.aop.SchedulerProxyScheduledLockAopBeanPostProcessor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.annotation.Role;
import org.springframework.core.type.AnnotationMetadata;

@Configuration
@Import(SchedulerProxyLockConfiguration.Registrar.class)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
class SchedulerProxyLockConfiguration {

    static class Registrar implements ImportBeanDefinitionRegistrar {
        private static final String BEAN_NAME = "schedulerProxyScheduledLockAopBeanPostProcessor";

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
            if (!registry.containsBeanDefinition(BEAN_NAME)) {
                AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder
                    .genericBeanDefinition(SchedulerProxyScheduledLockAopBeanPostProcessor.class)
                    .addConstructorArgValue("PT10M") //fixme
                    .addConstructorArgValue("PT10M") //fixme
                    .addConstructorArgReference("lockProvider") //fixme
                    .setRole(BeanDefinition.ROLE_INFRASTRUCTURE)
                    .getBeanDefinition();

                // We don't need this one to be post processed otherwise it can cause a
                // cascade of bean instantiation that we would rather avoid.
                beanDefinition.setSynthetic(true); // fixme
                registry.registerBeanDefinition(BEAN_NAME, beanDefinition);
            }
        }
    }
}
