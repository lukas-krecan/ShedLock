package net.javacrumbs.shedlock.spring.aop;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

public class MethodProxyAopFailureTest {
    @Test
    public void shouldFailOnStartupWithoutLockProvider() {
        assertThatThrownBy(() -> new AnnotationConfigApplicationContext(BeanCreationException.class))
            .isInstanceOf(BeanCreationException.class);
    }

    @Configuration
    @EnableSchedulerLock(defaultLockAtMostFor = "60s")
    public static class NoLockProviderConfig {

    }

}
