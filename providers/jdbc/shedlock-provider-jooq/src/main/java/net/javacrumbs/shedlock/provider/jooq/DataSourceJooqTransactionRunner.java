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
import org.jooq.DSLContext;
import org.jooq.TransactionalCallable;

final class DataSourceJooqTransactionRunner implements JooqTransactionRunner {
    private final DataSource dataSource;

    DataSourceJooqTransactionRunner(DataSource dataSource) {
        this.dataSource = requireNonNull(dataSource, "dataSource can not be null");
    }

    @Override
    public Object runInTransaction(DSLContext dslContext, TransactionalCallable<?> txCallable) {
        return dslContext.configuration().derive(dataSource).dsl().transactionResult(txCallable);
    }
}
