package net.javacrumbs.shedlock.spring.aop;

import java.lang.reflect.Method;
import net.javacrumbs.shedlock.core.LockProvider;

/**
 * Not public now. If you think you need your LockProviderSupplier please create an issue.
 */
@FunctionalInterface
interface LockProviderSupplier {
    LockProvider supply(Object target, Method method, Object[] parameterValues);
}
