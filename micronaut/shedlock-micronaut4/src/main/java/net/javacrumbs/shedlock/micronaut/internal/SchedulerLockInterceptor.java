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
package net.javacrumbs.shedlock.micronaut.internal;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.convert.ConversionService;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.Optional;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.support.annotation.Nullable;

@Singleton
public class SchedulerLockInterceptor implements MethodInterceptor<Object, Object> {
    private final LockingTaskExecutor lockingTaskExecutor;
    private final MicronautLockConfigurationExtractor micronautLockConfigurationExtractor;

    public SchedulerLockInterceptor(
            LockProvider lockProvider,
            Optional<ConversionService> conversionService,
            @Value("${shedlock.defaults.lock-at-most-for}") String defaultLockAtMostFor,
            @Value("${shedlock.defaults.lock-at-least-for:PT0S}") String defaultLockAtLeastFor) {
        /*
         * From Micronaut 3 to 4, ConversionService changes from a parameterized type to
         * a non-parameterized one, so some raw type usage and unchecked casts are done
         * to support both Micronaut versions.
         */
        ConversionService resolvedConversionService = conversionService.orElse(ConversionService.SHARED);

        lockingTaskExecutor = new DefaultLockingTaskExecutor(lockProvider);

        micronautLockConfigurationExtractor = new MicronautLockConfigurationExtractor(
                convert(resolvedConversionService, defaultLockAtMostFor, "defaultLockAtMostFor"),
                convert(resolvedConversionService, defaultLockAtLeastFor, "defaultLockAtLeastFor"),
                resolvedConversionService);
    }

    private static Duration convert(
            ConversionService resolvedConversionService, String defaultLockAtMostFor, String label) {
        return resolvedConversionService
                .convert(defaultLockAtMostFor, Duration.class)
                .orElseThrow(() -> new IllegalArgumentException("Invalid '" + label + "' value"));
    }

    @Override
    @Nullable
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Class<?> returnType = context.getReturnType().getType();
        if (!void.class.equals(returnType) && !Void.class.equals(returnType)) {
            throw new LockingNotSupportedException();
        }

        Optional<LockConfiguration> lockConfiguration =
                micronautLockConfigurationExtractor.getLockConfiguration(context.getExecutableMethod());
        if (lockConfiguration.isPresent()) {
            lockingTaskExecutor.executeWithLock((Runnable) context::proceed, lockConfiguration.get());
            return null;
        } else {
            return context.proceed();
        }
    }
}
