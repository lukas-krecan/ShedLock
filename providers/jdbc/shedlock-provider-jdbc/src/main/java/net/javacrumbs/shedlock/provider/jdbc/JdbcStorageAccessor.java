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
package net.javacrumbs.shedlock.provider.jdbc;

import net.javacrumbs.shedlock.provider.jdbc.internal.AbstractJdbcStorageAccessor;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.BiFunction;

import static java.util.Objects.requireNonNull;

class JdbcStorageAccessor extends AbstractJdbcStorageAccessor {
    private final DataSource dataSource;

    JdbcStorageAccessor(@NonNull DataSource dataSource, @NonNull String tableName) {
        super(tableName);
        this.dataSource = requireNonNull(dataSource, "dataSource can not be null");
    }

    @Override
    protected <T> T executeCommand(
        String sql,
        SqlFunction<PreparedStatement, T> body,
        BiFunction<String, SQLException, T> exceptionHandler
    ) {
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutocommit = connection.getAutoCommit();
            if (!originalAutocommit) {
                connection.setAutoCommit(true);
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                return body.apply(statement);
            } catch (SQLException e) {
                return exceptionHandler.apply(sql, e);
            } finally {
                // Cleanup
                if (!originalAutocommit) {
                    connection.setAutoCommit(false);
                }
            }
        } catch (SQLException e) {
            return exceptionHandler.apply(sql, e);
        }
    }
}
