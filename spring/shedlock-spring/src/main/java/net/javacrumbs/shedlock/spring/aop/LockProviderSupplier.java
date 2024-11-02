package net.javacrumbs.shedlock.spring.aop;

import java.lang.reflect.Method;
import net.javacrumbs.shedlock.core.LockProvider;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

/**
 * Not public now. If you think you need your LockProviderSupplier please create an issue and explain your use-case.
 */
@FunctionalInterface
interface LockProviderSupplier {
    LockProvider supply(Object target, Method method, Object[] parameterValues);

    static LockProviderSupplier create(ListableBeanFactory beanFactory) {
        // Only fetching beanNames as the beans might not have been initialized yet.
        String[] beanNamesForType = beanFactory.getBeanNamesForType(LockProvider.class);
        if (beanNamesForType.length == 0) {
            throw new NoSuchBeanDefinitionException(LockProvider.class, "No LockProvider bean found.");
        }
        if (beanNamesForType.length == 1) {
            return (target, method, arguments) -> beanFactory.getBean(LockProvider.class);
        }
        return new BeanNameSelectingLockProviderSupplier(beanFactory);
    }
}
