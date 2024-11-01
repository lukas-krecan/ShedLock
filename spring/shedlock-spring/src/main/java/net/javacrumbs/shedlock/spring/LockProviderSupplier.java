package net.javacrumbs.shedlock.spring;

import java.lang.reflect.Method;
import net.javacrumbs.shedlock.core.LockProvider;

@FunctionalInterface
public interface LockProviderSupplier {
    LockProvider supply(Object target, Method method, Object[] parameterValues);
}
