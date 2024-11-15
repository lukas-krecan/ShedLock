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
package net.javacrumbs.shedlock.spring.aop;

import static net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock.InterceptMode;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.AutoProxyRegistrar;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

public class SchedulerLockConfigurationSelector implements ImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                metadata.getAnnotationAttributes(EnableSchedulerLock.class.getName(), false));
        InterceptMode mode = attributes.getEnum("interceptMode");
        return switch (mode) {
            case PROXY_METHOD -> new String[] {
                AutoProxyRegistrar.class.getName(),
                LockConfigurationExtractorConfiguration.class.getName(),
                MethodProxyLockConfiguration.class.getName()
            };
            case PROXY_SCHEDULER -> new String[] {
                AutoProxyRegistrar.class.getName(),
                LockConfigurationExtractorConfiguration.class.getName(),
                SchedulerProxyLockConfiguration.class.getName(),
                RegisterDefaultTaskSchedulerPostProcessor.class.getName()
            };
        };
    }
}
