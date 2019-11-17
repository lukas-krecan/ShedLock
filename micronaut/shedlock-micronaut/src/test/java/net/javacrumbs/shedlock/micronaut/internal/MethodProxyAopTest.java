/**
 * Copyright 2009-2019 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.micronaut.internal;

import io.micronaut.test.annotation.MicronautTest;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.micronaut.internal.MethodProxyAopConfig.AnotherTestBean;
import net.javacrumbs.shedlock.micronaut.internal.MethodProxyAopConfig.TestBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Optional;

import static net.javacrumbs.shedlock.micronaut.internal.TestUtils.hasParams;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;


@MicronautTest
class MethodProxyAopTest {
    @Inject
    private LockProvider lockProvider;

    @Inject
    private TestBean testBean;

    @Inject
    private AnotherTestBean anotherTestBean;

    private final SimpleLock simpleLock = mock(SimpleLock.class);

    @BeforeEach
    void prepareMocks() {
        Mockito.reset(lockProvider, simpleLock);
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLock));
    }

    @Test
    void shouldNotCollLockProviderWithNoAnnotation() {
        testBean.noAnnotation();
        verifyZeroInteractions(lockProvider);
    }

    @Test
    void shouldCallLockProviderOnDirectCall() {
        testBean.normal();
        verify(lockProvider).lock(hasParams("normal", 30_000, 100));
        verify(simpleLock).unlock();
    }

    @Test
    void shouldRethrowRuntimeException() {
        assertThatThrownBy(() -> testBean.throwsRuntimeException()).isInstanceOf(RuntimeException.class);
        verify(lockProvider).lock(hasParams("runtimeException", 100, 100));
        verify(simpleLock).unlock();
    }

    @Test
    void shouldRethrowDeclaredException() {
        assertThatThrownBy(() -> testBean.throwsException()).isInstanceOf(IOException.class);
        verify(lockProvider).lock(hasParams("exception", 30_000, 100));
        verify(simpleLock).unlock();
    }

    @Test
    void shouldFailOnReturnType() {
        assertThatThrownBy(() -> testBean.returnsValue()).isInstanceOf(LockingNotSupportedException.class);
        verifyZeroInteractions(lockProvider);
    }

    @Test
    void shouldReadConfigurationProperty() {
        testBean.property();
        verify(lockProvider).lock(hasParams("property", 30_000, 1_000));
        verify(simpleLock).unlock();
    }

    @Test
    void shouldReadAnnotationFromImplementationClass() {
        anotherTestBean.runManually();
        verify(lockProvider).lock(hasParams("classAnnotation", 30_000, 100));
        verify(simpleLock).unlock();
    }
}
