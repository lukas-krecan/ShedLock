package net.javacrumbs.shedlock.spring.aop;

import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;

import java.lang.reflect.Method;
import java.util.Map;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.annotation.LockProviderBeanName;
import net.javacrumbs.shedlock.support.annotation.Nullable;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;

class DefaultLockProviderSupplier implements LockProviderSupplier {
    private final ListableBeanFactory beanFactory;

    DefaultLockProviderSupplier(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public LockProvider supply(Object target, Method method, Object[] parameterValues) {
        Map<String, LockProvider> lockProviders = beanFactory.getBeansOfType(LockProvider.class);
        if (lockProviders.isEmpty()) {
            throw new NoSuchBeanDefinitionException(LockProvider.class, "No LockProvider bean found.");
        }
        if (lockProviders.size() == 1) {
            return lockProviders.values().iterator().next();
        }

        LockProviderBeanName annotation = findAnnotation(target, method);
        if (annotation == null) {
            throw new NoUniqueBeanDefinitionException(
                    LockProvider.class,
                    lockProviders.size(),
                    "Multiple LockProviders found (" + String.join(", ", lockProviders.keySet())
                            + "), use @LockProviderBeanName to disambiguate.");
        }
        return beanFactory.getBean(annotation.value(), LockProvider.class);
    }

    @Nullable
    private LockProviderBeanName findAnnotation(Object target, Method method) {
        LockProviderBeanName annotation = findMergedAnnotation(method, LockProviderBeanName.class);
        if (annotation != null) {
            return annotation;
        }
        annotation = findMergedAnnotation(target.getClass(), LockProviderBeanName.class);
        if (annotation != null) {
            return annotation;
        }
        return method.getDeclaringClass().getPackage().getAnnotation(LockProviderBeanName.class);
    }
}
