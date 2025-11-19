package net.javacrumbs.shedlock.spring.aop;

import java.lang.reflect.Method;
import java.util.Map;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.annotation.LockProviderToUse;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.core.annotation.AnnotationUtils;

class BeanNameSelectingLockProviderSupplier implements LockProviderSupplier {
    private final ListableBeanFactory beanFactory;

    BeanNameSelectingLockProviderSupplier(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public LockProvider supply(@Nullable Object target, Method method, @Nullable Object[] parameterValues) {
        LockProviderToUse annotation = findAnnotation(target, method);
        if (annotation == null) {
            throw noUniqueBeanDefinitionException();
        }
        return beanFactory.getBean(annotation.value(), LockProvider.class);
    }

    private NoUniqueBeanDefinitionException noUniqueBeanDefinitionException() {
        Map<String, LockProvider> lockProviders = beanFactory.getBeansOfType(LockProvider.class);
        return new NoUniqueBeanDefinitionException(
                LockProvider.class,
                lockProviders.size(),
                "Multiple LockProviders found (" + String.join(", ", lockProviders.keySet())
                        + "), use @LockProviderToUse to disambiguate.");
    }

    private @Nullable LockProviderToUse findAnnotation(@Nullable Object target, Method method) {
        LockProviderToUse annotation = AnnotationUtils.findAnnotation(method, LockProviderToUse.class);
        if (annotation != null) {
            return annotation;
        }
        if (target != null) {
            annotation = AnnotationUtils.findAnnotation(target.getClass(), LockProviderToUse.class);
            if (annotation != null) {
                return annotation;
            }
        }
        return method.getDeclaringClass().getPackage().getAnnotation(LockProviderToUse.class);
    }
}
