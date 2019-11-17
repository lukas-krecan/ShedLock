/**
 * Copyright 2009-2019 the original author or authors.
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
package net.javacrumbs.shedlock.micronaut.internal;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;

import javax.inject.Singleton;
import java.time.Duration;
import java.util.Optional;

@Singleton
public
class SchedulerLockInterceptor implements MethodInterceptor<Object, Object> {
    private final LockingTaskExecutor lockingTaskExecutor;
    private final MicronautLockConfigurationExtractor micronautLockConfigurationExtractor;

    public SchedulerLockInterceptor(LockProvider lockProvider) {
        lockingTaskExecutor = new DefaultLockingTaskExecutor(lockProvider);
        micronautLockConfigurationExtractor = new MicronautLockConfigurationExtractor(Duration.ofSeconds(100), Duration.ofSeconds(10)); //FIXME
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Optional<LockConfiguration> lockConfiguration = micronautLockConfigurationExtractor.getLockConfiguration(context.getExecutableMethod());
        if (lockConfiguration.isPresent()) {
            lockingTaskExecutor.executeWithLock((Runnable) context::proceed, lockConfiguration.get());
            return null;
        } else {
            return context.proceed();
        }
    }
}
