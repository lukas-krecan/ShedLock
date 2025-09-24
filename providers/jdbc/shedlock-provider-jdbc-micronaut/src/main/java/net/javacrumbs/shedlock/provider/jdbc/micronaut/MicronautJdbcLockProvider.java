/**
 * Copyright 2009-2021 the original author or authors.
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
package net.javacrumbs.shedlock.provider.jdbc.micronaut;

import static io.micronaut.transaction.TransactionDefinition.Propagation.REQUIRES_NEW;
import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.provider.sql.SqlConfiguration.DEFAULT_TABLE_NAME;

import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import java.sql.Connection;
import java.util.TimeZone;
import net.javacrumbs.shedlock.provider.sql.DatabaseProduct;
import net.javacrumbs.shedlock.provider.sql.SqlConfiguration;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lock provided by plain JDBC, using the Micronaut Data transaction manager. It
 * uses a table that contains lock_name and locked_until.
 *
 * <ol>
 * <li>Attempts to insert a new lock record. Since lock name is a primary key,
 * it fails if the record already exists. As an optimization, we keep in-memory
 * track of created lock records.
 * <li>If the insert succeeds (1 inserted row) we have the lock.
 * <li>If the insert failed due to duplicate key or we have skipped the
 * insertion, we will try to update lock record using UPDATE tableName SET
 * lock_until = :lockUntil WHERE name = :lockName AND lock_until &lt;= :now
 * <li>If the update succeeded (1 updated row), we have the lock. If the update
 * failed (0 updated rows) somebody else holds the lock
 * <li>When unlocking, lock_until is set to now.
 * </ol>
 */
public class MicronautJdbcLockProvider extends StorageBasedLockProvider {

    public MicronautJdbcLockProvider(TransactionOperations<Connection> transactionOperations) {
        this(transactionOperations, DEFAULT_TABLE_NAME);
    }

    public MicronautJdbcLockProvider(TransactionOperations<Connection> transactionOperations, String tableName) {
        this(Configuration.builder(transactionOperations)
                .withTableName(tableName)
                .build());
    }

    public MicronautJdbcLockProvider(Configuration configuration) {
        super(new MicronautJdbcStorageAccessor(configuration));
    }

    public static final class Configuration extends SqlConfiguration {
        private final TransactionOperations<Connection> transactionOperations;

        private static final Logger logger = LoggerFactory.getLogger(Configuration.class);

        Configuration(
                TransactionOperations<Connection> transactionOperations,
                @Nullable DatabaseProduct databaseProduct,
                String tableName,
                @Nullable TimeZone timeZone,
                ColumnNames columnNames,
                String lockedByValue,
                boolean useDbTime,
                @Nullable Integer isolationLevel,
                boolean throwUnexpectedException) {

            super(
                    databaseProduct,
                    tableName,
                    timeZone,
                    columnNames,
                    lockedByValue,
                    useDbTime,
                    isolationLevel,
                    throwUnexpectedException);
            this.transactionOperations = requireNonNull(transactionOperations, "transactionOperations can not be null");
        }

        public TransactionOperations<Connection> getTransactionOperations() {
            return transactionOperations;
        }

        @Override
        public DatabaseProduct getDatabaseProduct() {
            if (super.getDatabaseProduct() != null) {
                return super.getDatabaseProduct();
            }

            return transactionOperations.execute(TransactionDefinition.of(REQUIRES_NEW), connection -> {
                try {
                    String jdbcProductName =
                            connection.getConnection().getMetaData().getDatabaseProductName();
                    return DatabaseProduct.matchProductName(jdbcProductName);
                } catch (Exception e) {
                    logger.debug("Can not determine database product name {}", e.getMessage());
                    return DatabaseProduct.UNKNOWN;
                }
            });
        }

        public static Configuration.Builder builder(TransactionOperations<Connection> transactionOperations) {
            return new Configuration.Builder(transactionOperations);
        }

        public static final class Builder extends SqlConfigurationBuilder<Builder> {
            private final TransactionOperations<Connection> transactionOperations;

            public Builder(TransactionOperations<Connection> transactionOperations) {
                this.transactionOperations = transactionOperations;
            }

            public Configuration build() {
                return new Configuration(
                        transactionOperations,
                        databaseProduct,
                        dbUpperCase ? tableName.toUpperCase() : tableName,
                        timeZone,
                        dbUpperCase ? columnNames.toUpperCase() : columnNames,
                        lockedByValue,
                        useDbTime,
                        isolationLevel,
                        throwUnexpectedException);
            }
        }
    }
}
