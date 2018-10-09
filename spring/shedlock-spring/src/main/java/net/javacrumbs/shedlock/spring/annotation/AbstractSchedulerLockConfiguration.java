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

import org.springframework.context.annotation.ImportAware;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

class AbstractSchedulerLockConfiguration implements ImportAware {
    private AnnotationAttributes annotationAttributes;

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
}
