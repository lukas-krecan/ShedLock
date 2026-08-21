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

import javax.sql.DataSource;
import net.javacrumbs.shedlock.support.NewTransactionRunner;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import org.jooq.DSLContext;

public class JooqLockProvider extends StorageBasedLockProvider {
    public JooqLockProvider(DSLContext dslContext) {
        super(new JooqStorageAccessor(dslContext));
    }

    public JooqLockProvider(DSLContext dslContext, NewTransactionRunner newTransactionRunner) {
        super(new JooqStorageAccessor(dslContext, newTransactionRunner));
    }

    /**
     * Like {@link #JooqLockProvider(DSLContext)}, but every lock operation runs on a fresh
     * connection obtained directly from {@code dataSource}, instead of whatever connection {@code
     * dslContext} happens to be using. Unlike {@link #JooqLockProvider(DSLContext,
     * NewTransactionRunner)}, this works even when {@code dslContext} is bound directly to a raw
     * {@link java.sql.Connection} that may already have an ambient transaction open (e.g. {@code
     * DSL.using(connection, dialect)}) - a construction {@code NewTransactionRunner} cannot help
     * with, since it has no independent connection to switch to. Use this constructor if {@code
     * dslContext} may be bound to a connection that is shared with, or participates in, a
     * transaction the calling code manages itself, and {@code dslContext} isn't guaranteed to be
     * Spring-transaction-aware.
     */
    public JooqLockProvider(DSLContext dslContext, DataSource dataSource) {
        super(new JooqStorageAccessor(dslContext, dataSource));
    }
}
