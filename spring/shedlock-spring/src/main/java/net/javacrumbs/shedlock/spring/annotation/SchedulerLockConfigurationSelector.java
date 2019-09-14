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

import org.springframework.context.annotation.AutoProxyRegistrar;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

import static net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock.InterceptMode;
import static net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock.InterceptMode.PROXY_METHOD;
import static net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock.InterceptMode.PROXY_SCHEDULER;

class SchedulerLockConfigurationSelector implements ImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(metadata.getAnnotationAttributes(EnableSchedulerLock.class.getName(), false));
        InterceptMode mode = attributes.getEnum("interceptMode");
        if (mode == PROXY_METHOD) {
            return new String[]{AutoProxyRegistrar.class.getName(), MethodProxyLockConfiguration.class.getName()};
        } else if (mode == PROXY_SCHEDULER) {
            return new String[]{AutoProxyRegistrar.class.getName(), SchedulerProxyLockConfiguration.class.getName(), RegisterDefaultTaskSchedulerPostProcessor.class.getName()};
        } else {
            throw new UnsupportedOperationException("Unknown mode " + mode);
        }

    }
}
