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
package net.javacrumbs.shedlock.quarkus.test;

import io.quarkus.test.junit.QuarkusTest;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.quarkus.test.QuarkusConfig.AnotherTestBean;
import net.javacrumbs.shedlock.quarkus.test.QuarkusConfig.TestBean;
import net.javacrumbs.shedlock.support.LockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Optional;

import static net.javacrumbs.shedlock.quarkus.test.TestUtils.hasParams;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


@QuarkusTest
class QuarkusShedlockTest {
    @Inject
    LockProvider lockProvider;

    @Inject
    TestBean testBean;

    @Inject
    AnotherTestBean anotherTestBean;

    private final SimpleLock simpleLock = mock(SimpleLock.class);

    @BeforeEach
    void prepareMocks() {
        Mockito.reset(lockProvider, simpleLock);
        when(lockProvider.lock(any())).thenReturn(Optional.of(simpleLock));
    }

    @Test
    void shouldNotCallLockProviderWithNoAnnotation() {
        assertThatThrownBy(() -> testBean.noAnnotation()).hasMessageStartingWith("The task is not locked.");
        verifyNoInteractions(lockProvider);
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
        assertThatThrownBy(() -> testBean.returnsValue()).isInstanceOf(LockException.class);
        verifyNoInteractions(lockProvider);
    }

    @Test
    @Disabled // Not implemented, waiting if anyone is going to use it. When needed, get the code from Quarkus SchedulerUtils
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
