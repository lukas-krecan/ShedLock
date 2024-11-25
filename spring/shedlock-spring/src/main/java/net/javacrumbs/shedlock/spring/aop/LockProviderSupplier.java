package net.javacrumbs.shedlock.spring.aop;

import java.lang.reflect.Method;
import net.javacrumbs.shedlock.core.LockProvider;
import org.springframework.beans.factory.ListableBeanFactory;

/**
 * Not public now. If you think you need your LockProviderSupplier please create an issue and explain your use-case.
 */
@FunctionalInterface
interface LockProviderSupplier {
    LockProvider supply(Object target, Method method, Object[] parameterValues);

    static LockProviderSupplier create(ListableBeanFactory beanFactory) {
        // Only fetching beanNames as the beans might not have been initialized yet.
        String[] beanNamesForType = beanFactory.getBeanNamesForType(LockProvider.class);
        // If there are no beans of LockProvider type, we can't fail here as in older version we
        // did not fail, and it's quite common in the tests. To maintain backward compatibility
        // the failure will happen in runtime.
        if (beanNamesForType.length <= 1) {
            return (target, method, arguments) -> beanFactory.getBean(LockProvider.class);
        }
        return new BeanNameSelectingLockProviderSupplier(beanFactory);
    }
}
