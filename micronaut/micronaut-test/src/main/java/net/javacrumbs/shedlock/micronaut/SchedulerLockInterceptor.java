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
package net.javacrumbs.shedlock.micronaut;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import net.javacrumbs.shedlock.core.DefaultLockManager;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockConfigurationExtractor;
import net.javacrumbs.shedlock.core.LockManager;
import net.javacrumbs.shedlock.core.LockProvider;

import javax.inject.Singleton;
import java.time.Instant;
import java.util.Optional;

@Singleton
public class SchedulerLockInterceptor implements MethodInterceptor<Object, Object> {
    private final LockManager lockManager;

    public SchedulerLockInterceptor(LockProvider lockProvider) {
        lockManager = new DefaultLockManager(lockProvider, new LockConfigurationExtractor() {
            @Override
            public Optional<LockConfiguration> getLockConfiguration(Runnable task) {
                return Optional.of(new LockConfiguration("test", Instant.now().plusSeconds(10)));
            }
        });
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        lockManager.executeWithLock(context::proceed);
        return null;
    }
}
