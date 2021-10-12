/**
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.spring.aop;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.aop.DeprecatedMethodProxyAopConfig.AnotherTestBean;
import net.javacrumbs.shedlock.spring.aop.DeprecatedMethodProxyAopConfig.TestBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.util.Optional;

import static net.javacrumbs.shedlock.spring.TestUtils.hasParams;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DeprecatedMethodProxyAopConfig.class)
public class DeprecatedMethodProxyAopTest {
    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private TestBean testBean;

    @Autowired
    private AnotherTestBean anotherTestBean;

    private final SimpleLock simpleLock = mock(SimpleLock.class);

    @BeforeEach
    public void prepareMocks() {
        Mockito.reset(lockProvider, simpleLock);
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLock));

    }

    @Test
    public void shouldNotCollLockProviderWithNoAnnotation() {
        testBean.noAnnotation();
        verifyNoInteractions(lockProvider);
    }

    @Test
    public void shouldCallLockProviderOnDirectCall() {
        testBean.normal();
        verify(lockProvider).lock(hasParams("normal", 30_000, 100));
        verify(simpleLock).unlock();
    }

    @Test
    public void shouldUseCustomAnnotation() {
        testBean.custom();
        verify(lockProvider).lock(hasParams("custom", 30_000, 100));
        verify(simpleLock).unlock();
    }

    @Test
    public void shouldRethrowRuntimeException() {
        assertThatThrownBy(() -> testBean.throwsRuntimeException()).isInstanceOf(RuntimeException.class);
        verify(lockProvider).lock(hasParams("runtimeException", 100, 100));
        verify(simpleLock).unlock();
    }

    @Test
    public void shouldRethrowDeclaredException() {
        assertThatThrownBy(() -> testBean.throwsException()).isInstanceOf(IOException.class);
        verify(lockProvider).lock(hasParams("exception", 30_000, 100));
        verify(simpleLock).unlock();
    }

    @Test
    public void shouldFailOnReturnType() {
        assertThatThrownBy(() -> testBean.returnsValue()).isInstanceOf(LockingNotSupportedException.class);
        verifyNoInteractions(lockProvider);
    }

    @Test
    public void shouldReadSpringProperty() {
        testBean.spel();
        verify(lockProvider).lock(hasParams("spel", 30_000, 1_000));
        verify(simpleLock).unlock();
    }

    @Test
    public void shouldReadAnnotationFromImplementationClass() {
        anotherTestBean.runManually();
        verify(lockProvider).lock(hasParams("classAnnotation", 30_000, 100));
        verify(simpleLock).unlock();
    }
}
