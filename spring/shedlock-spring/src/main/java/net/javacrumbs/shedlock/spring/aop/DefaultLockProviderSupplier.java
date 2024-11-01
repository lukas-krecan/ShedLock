package net.javacrumbs.shedlock.spring.aop;

import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;

import java.lang.reflect.Method;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.annotation.LockProviderBeanName;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.util.StringUtils;

class DefaultLockProviderSupplier implements LockProviderSupplier {
    private final BeanFactory beanFactory;

    DefaultLockProviderSupplier(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public LockProvider supply(Object target, Method method, Object[] parameterValues) {
        // TODO: Cache
        LockProviderBeanName annotation = findMergedAnnotation(method, LockProviderBeanName.class);
        if (annotation == null) {
            annotation = findMergedAnnotation(method.getDeclaringClass(), LockProviderBeanName.class);
        }
        if (annotation != null && StringUtils.hasText(annotation.value())) {
            return beanFactory.getBean(annotation.value(), LockProvider.class);
        } else {
            return beanFactory.getBean(LockProvider.class);
        }
    }
}
