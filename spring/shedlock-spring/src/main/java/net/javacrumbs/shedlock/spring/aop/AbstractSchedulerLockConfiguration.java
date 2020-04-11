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

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.StringValueResolver;

import java.time.Duration;

class AbstractSchedulerLockConfiguration implements ImportAware, EmbeddedValueResolverAware {
    private AnnotationAttributes annotationAttributes;
    private StringValueResolver resolver;

    private final StringToDurationConverter durationConverter = StringToDurationConverter.INSTANCE;

    protected String getDefaultLockAtLeastFor() {
        return getStringFromAnnotation("defaultLockAtLeastFor");
    }

    protected String getDefaultLockAtMostFor() {
        return getStringFromAnnotation("defaultLockAtMostFor");
    }

    protected String getStringFromAnnotation(String defaultLockAtLeastFor) {
        return annotationAttributes.getString(defaultLockAtLeastFor);
    }

    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        this.annotationAttributes = AnnotationAttributes.fromMap(
            importMetadata.getAnnotationAttributes(EnableSchedulerLock.class.getName(), false));
        if (this.annotationAttributes == null) {
            throw new IllegalArgumentException(
                "@EnableSchedulerLock is not present on importing class " + importMetadata.getClassName());
        }
    }

    @Override
    public void setEmbeddedValueResolver(StringValueResolver resolver) {
        this.resolver = resolver;
    }

    protected Duration defaultLockAtLeastForDuration() {
        return toDuration(getDefaultLockAtLeastFor());
    }

    protected Duration defaultLockAtMostForDuration() {
        return toDuration(getDefaultLockAtMostFor());
    }

    protected StringValueResolver getResolver() {
        return resolver;
    }

    public StringToDurationConverter getDurationConverter() {
        return durationConverter;
    }

    private Duration toDuration(String string) {
        return durationConverter.convert(resolver.resolveStringValue(string));
    }
}
