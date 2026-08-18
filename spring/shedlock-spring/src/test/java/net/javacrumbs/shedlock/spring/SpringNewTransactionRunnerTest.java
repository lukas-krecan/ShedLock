/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package net.javacrumbs.shedlock.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class SpringNewTransactionRunnerTest {
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);
    private final SpringNewTransactionRunner transactionRunner = new SpringNewTransactionRunner(transactionManager);

    @Test
    void shouldRunInRequiresNewTransaction() throws Throwable {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);

        Object result = transactionRunner.runInNewTransaction(() -> "test");

        assertThat(result).isEqualTo("test");
        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        verify(transactionManager).commit(transactionStatus);
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void shouldRollbackAndRethrowCheckedException() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        IOException exception = new IOException("test");

        Throwable thrown = catchThrowable(() -> transactionRunner.runInNewTransaction(() -> {
            throw exception;
        }));

        assertThat(thrown).isSameAs(exception);
        verify(transactionManager).rollback(transactionStatus);
    }
}
