package net.javacrumbs.shedlock.spring.aop;


import java.lang.reflect.Method;
import java.util.Map;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.annotation.LockProviderBeanName;
import net.javacrumbs.shedlock.support.annotation.Nullable;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.core.annotation.AnnotationUtils;

class BeanNameSelectingLockProviderSupplier implements LockProviderSupplier {
    private final ListableBeanFactory beanFactory;

    BeanNameSelectingLockProviderSupplier(ListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public LockProvider supply(Object target, Method method, Object[] parameterValues) {
        LockProviderBeanName annotation = findAnnotation(target, method);
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
                + "), use @LockProviderBeanName to disambiguate.");
    }

    @Nullable
    private LockProviderBeanName findAnnotation(Object target, Method method) {
        LockProviderBeanName annotation = AnnotationUtils.findAnnotation(method, LockProviderBeanName.class);
        if (annotation != null) {
            return annotation;
        }
        annotation = AnnotationUtils.findAnnotation(target.getClass(), LockProviderBeanName.class);
        if (annotation != null) {
            return annotation;
        }
        return method.getDeclaringClass().getPackage().getAnnotation(LockProviderBeanName.class);
    }
}
