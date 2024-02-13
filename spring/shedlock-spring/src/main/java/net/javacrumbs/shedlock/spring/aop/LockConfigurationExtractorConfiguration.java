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

import java.time.Duration;
import net.javacrumbs.shedlock.spring.ExtendedLockConfigurationExtractor;
import net.javacrumbs.shedlock.support.annotation.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringValueResolver;

/** Defines ExtendedLockConfigurationExtractor bean. */
@Configuration
class LockConfigurationExtractorConfiguration extends AbstractLockConfiguration
        implements EmbeddedValueResolverAware, BeanFactoryAware {
    private final StringToDurationConverter durationConverter = StringToDurationConverter.INSTANCE;

    @Nullable
    private StringValueResolver resolver;

    @Nullable
    private BeanFactory beanFactory;

    @Bean
    ExtendedLockConfigurationExtractor lockConfigurationExtractor() {
        return new SpringLockConfigurationExtractor(
                defaultLockAtMostForDuration(),
                defaultLockAtLeastForDuration(),
                resolver,
                durationConverter,
                beanFactory);
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
    public void setEmbeddedValueResolver(StringValueResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
}
