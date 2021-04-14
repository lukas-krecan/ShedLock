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

import net.javacrumbs.shedlock.spring.ExtendedLockConfigurationExtractor;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringValueResolver;

import java.time.Duration;

/**
 * Defines ExtendedLockConfigurationExtractor bean.
 */
@Configuration
class LockConfigurationExtractorConfiguration extends AbstractLockConfiguration implements EmbeddedValueResolverAware {
    private final StringToDurationConverter durationConverter = StringToDurationConverter.INSTANCE;

    private StringValueResolver resolver;

    @Bean
    ExtendedLockConfigurationExtractor lockConfigurationExtractor() {
        return new SpringLockConfigurationExtractor(defaultLockAtMostForDuration(), defaultLockAtLeastForDuration(), resolver, durationConverter);
    }

    private Duration defaultLockAtLeastForDuration() {
        return toDuration(getDefaultLockAtLeastFor());
    }

    private Duration defaultLockAtMostForDuration() {
        return toDuration(getDefaultLockAtMostFor());
    }

    private String getDefaultLockAtLeastFor() {
        return getStringFromAnnotation("defaultLockAtLeastFor");
    }

    private String getDefaultLockAtMostFor() {
        return getStringFromAnnotation("defaultLockAtMostFor");
    }

    private Duration toDuration(String string) {
        return durationConverter.convert(resolver.resolveStringValue(string));
    }

    protected String getStringFromAnnotation(String name) {
        return annotationAttributes.getString(name);
    }

    @Override
    public void setEmbeddedValueResolver(@NonNull StringValueResolver resolver) {
        this.resolver = resolver;
    }
}
