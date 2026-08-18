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

import static java.util.Objects.requireNonNull;

import net.javacrumbs.shedlock.support.NewTransactionRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs lock storage operations in a Spring {@code PROPAGATION_REQUIRES_NEW} transaction. */
public final class SpringNewTransactionRunner implements NewTransactionRunner {
    private final TransactionTemplate transactionTemplate;

    public SpringNewTransactionRunner(PlatformTransactionManager transactionManager) {
        this(new TransactionTemplate(requireNonNull(transactionManager, "transactionManager can not be null")));
    }

    public SpringNewTransactionRunner(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = requireNonNull(transactionTemplate, "transactionTemplate can not be null");
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public Object runInNewTransaction(TransactionCallback callback) throws Throwable {
        try {
            return transactionTemplate.execute(status -> {
                try {
                    return callback.execute();
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable e) {
                    throw new TransactionCallbackException(e);
                }
            });
        } catch (TransactionCallbackException e) {
            throw e.getCause();
        }
    }

    private static final class TransactionCallbackException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private TransactionCallbackException(Throwable cause) {
            super(cause);
        }
    }
}
