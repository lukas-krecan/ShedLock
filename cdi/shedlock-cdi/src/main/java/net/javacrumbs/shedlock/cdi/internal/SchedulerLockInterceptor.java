package net.javacrumbs.shedlock.cdi.internal;

import static net.javacrumbs.shedlock.cdi.internal.Utils.parseDuration;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import net.javacrumbs.shedlock.cdi.SchedulerLock;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.eclipse.microprofile.config.ConfigProvider;

@SchedulerLock(name = "?")
@Priority(3001)
@Interceptor
public class SchedulerLockInterceptor {
    private final LockingTaskExecutor lockingTaskExecutor;
    private final CdiLockConfigurationExtractor lockConfigurationExtractor;

    @Inject
    public SchedulerLockInterceptor(LockProvider lockProvider) {
        this.lockingTaskExecutor = new DefaultLockingTaskExecutor(lockProvider);
        String lockAtMostFor = getConfigValue("shedlock.defaults.lock-at-most-for");
        String lockAtLeastFor = getConfigValue("shedlock.defaults.lock-at-least-for");
        Objects.requireNonNull(lockAtMostFor, "shedlock.defaults.lock-at-most-for parameter is mandatory");
        this.lockConfigurationExtractor = new CdiLockConfigurationExtractor(
                parseDuration(lockAtMostFor), lockAtLeastFor != null ? parseDuration(lockAtLeastFor) : Duration.ZERO);
    }

    private static String getConfigValue(String propertyName) {
        return ConfigProvider.getConfig().getConfigValue(propertyName).getValue();
    }

    @AroundInvoke
    Object lock(InvocationContext context) throws Throwable {
        Class<?> returnType = context.getMethod().getReturnType();
        if (!void.class.equals(returnType) && !Void.class.equals(returnType)) {
            throw new LockingNotSupportedException();
        }

        Optional<LockConfiguration> lockConfiguration =
                lockConfigurationExtractor.getLockConfiguration(context.getMethod());
        if (lockConfiguration.isPresent()) {
            lockingTaskExecutor.executeWithLock((LockingTaskExecutor.Task) context::proceed, lockConfiguration.get());
            return null;
        } else {
            return context.proceed();
        }
    }
}
