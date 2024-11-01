package net.javacrumbs.shedlock.spring.aop;

import static net.javacrumbs.shedlock.spring.aop.SpringLockConfigurationExtractor.findAnnotation;

import java.lang.reflect.Method;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.spring.LockProviderSupplier;
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
        LockProviderBeanName annotation = findAnnotation(target, method, LockProviderBeanName.class);
        if (annotation != null && StringUtils.hasText(annotation.value())) {
            return beanFactory.getBean(annotation.value(), LockProvider.class);
        } else {
            return beanFactory.getBean(LockProvider.class);
        }
    }
}
