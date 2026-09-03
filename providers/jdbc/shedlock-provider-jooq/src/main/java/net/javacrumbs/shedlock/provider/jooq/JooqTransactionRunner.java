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
package net.javacrumbs.shedlock.provider.jooq;

import static java.util.Objects.requireNonNull;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.support.NewTransactionRunner;
import org.jooq.DSLContext;
import org.jooq.TransactionalCallable;

/**
 * Runs jOOQ lock storage operations in a transaction.
 *
 * <p>Implementations may choose the {@link DSLContext} used for the transaction, for example by deriving one with a
 * different connection provider.
 */
@FunctionalInterface
public interface JooqTransactionRunner {

    Object runInTransaction(DSLContext dslContext, TransactionalCallable<?> txCallable) throws Throwable;

    static JooqTransactionRunner with(NewTransactionRunner newTransactionRunner) {
        requireNonNull(newTransactionRunner, "newTransactionRunner can not be null");
        return new NewTransactionRunnerAdapter(newTransactionRunner);
    }

    static JooqTransactionRunner usingDataSource(DataSource dataSource) {
        requireNonNull(dataSource, "dataSource can not be null");
        return new DataSourceJooqTransactionRunner(dataSource);
    }
}
