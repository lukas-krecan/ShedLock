/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.spring.aop;

import static net.javacrumbs.shedlock.spring.TestUtils.hasParams;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.aop.MultipleLockProvidersMethodProxyAopConfig.TestBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MultipleLockProvidersMethodProxyAopConfig.class)
public class MultipleLockProviderMethodProxyAopTest {
    @Autowired
    private LockProvider lockProvider1;

    @Autowired
    private LockProvider lockProvider2;

    @Autowired
    private TestBean testBean;

    private final SimpleLock simpleLock1 = mock(SimpleLock.class);
    private final SimpleLock simpleLock2 = mock(SimpleLock.class);

    @BeforeEach
    public void prepareMocks() {
        Mockito.reset(lockProvider1, lockProvider2, simpleLock1, simpleLock2);
        when(lockProvider1.lock(any())).thenReturn(Optional.of(simpleLock1));
        when(lockProvider2.lock(any())).thenReturn(Optional.of(simpleLock2));
        testBean.reset();
    }

    @Test
    public void shouldCallLockProvider1OnDirectCall() {
        testBean.method1();
        verify(lockProvider1).lock(hasParams("method1", 60_000, 0));
        verify(simpleLock1).unlock();
        assertThat(testBean.wasMethodCalled()).isTrue();
    }

    @Test
    public void shouldCallLockProvider2OnDirectCall() {
        testBean.method2();
        verify(lockProvider2).lock(hasParams("method2", 60_000, 0));
        verify(simpleLock2).unlock();
        assertThat(testBean.wasMethodCalled()).isTrue();
    }
}
