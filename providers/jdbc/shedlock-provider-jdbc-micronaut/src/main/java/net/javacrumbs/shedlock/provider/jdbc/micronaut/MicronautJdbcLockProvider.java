/**
 * Copyright 2009-2021 the original author or authors.
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
package net.javacrumbs.shedlock.provider.jdbc.micronaut;

import io.micronaut.transaction.TransactionOperations;
import io.micronaut.transaction.jdbc.DataSourceTransactionManager;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Lock provided by plain JDBC, using the Micronaut Data transaction manager. It uses a table that contains lock_name and locked_until.
 * <ol>
 * <li>
 * Attempts to insert a new lock record. Since lock name is a primary key, it fails if the record already exists. As an optimization,
 * we keep in-memory track of created  lock records.
 * </li>
 * <li>
 * If the insert succeeds (1 inserted row) we have the lock.
 * </li>
 * <li>
 * If the insert failed due to duplicate key or we have skipped the insertion, we will try to update lock record using
 * UPDATE tableName SET lock_until = :lockUntil WHERE name = :lockName AND lock_until &lt;= :now
 * </li>
 * <li>
 * If the update succeeded (1 updated row), we have the lock. If the update failed (0 updated rows) somebody else holds the lock
 * </li>
 * <li>
 * When unlocking, lock_until is set to now.
 * </li>
 * </ol>
 */
public class MicronautJdbcLockProvider extends StorageBasedLockProvider {

    private static final String DEFAULT_TABLE_NAME = "shedlock";

    public MicronautJdbcLockProvider(@NonNull DataSource datasource) {
        this(new DataSourceTransactionManager(datasource), DEFAULT_TABLE_NAME);
    }

    public MicronautJdbcLockProvider(@NonNull TransactionOperations<Connection> transactionManager) {
        this(transactionManager, DEFAULT_TABLE_NAME);
    }

    public MicronautJdbcLockProvider(@NonNull TransactionOperations<Connection> transactionManager, @NonNull String tableName) {
        super(new MicronautJdbcStorageAccessor(transactionManager, tableName));
    }
}
