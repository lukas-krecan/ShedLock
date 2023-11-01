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

import static java.util.Objects.requireNonNull;

import io.micronaut.transaction.TransactionDefinition;
import io.micronaut.transaction.TransactionOperations;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.BiFunction;
import net.javacrumbs.shedlock.provider.jdbc.internal.AbstractJdbcStorageAccessor;
import net.javacrumbs.shedlock.support.annotation.NonNull;

class MicronautJdbcStorageAccessor extends AbstractJdbcStorageAccessor {
    private final TransactionOperations<Connection> transactionManager;

    private final TransactionDefinition.Propagation propagation = TransactionDefinition.Propagation.REQUIRES_NEW;

    MicronautJdbcStorageAccessor(
            @NonNull TransactionOperations<Connection> transactionManager, @NonNull String tableName) {
        super(tableName);
        this.transactionManager = requireNonNull(transactionManager, "transactionManager can not be null");
    }

    @Override
    protected <T> T executeCommand(
            String sql, SqlFunction<PreparedStatement, T> body, BiFunction<String, SQLException, T> exceptionHandler) {
        return transactionManager.execute(TransactionDefinition.of(propagation), status -> {
            try (PreparedStatement statement = status.getConnection().prepareStatement(sql)) {
                return body.apply(statement);
            } catch (SQLException e) {
                return exceptionHandler.apply(sql, e);
            }
        });
    }
}
