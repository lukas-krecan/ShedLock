/*
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
package net.javacrumbs.shedlock.provider.r2dbc;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Statement;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

class R2dbcStorageAccessor extends AbstractR2dbcStorageAccessor {

    private final ConnectionFactory connectionFactory;
    private final String driver;

    private static final String MSSQL_NAME = "Microsoft SQL Server";
    private static final String MYSQL_NAME = "MySQL";
    private static final String POSTGRES_NAME = "PostgreSQL";
    private static final String H2_NAME = "H2";
    private static final String MARIA_NAME = "MariaDB";
    private static final String ORACLE_NAME = "Oracle Database";

    R2dbcStorageAccessor(@NonNull ConnectionFactory connectionFactory, @NonNull String tableName) {
        super(tableName);
        this.connectionFactory = requireNonNull(connectionFactory, "dataSource can not be null");
        this.driver = connectionFactory.getMetadata().getName();
    }

    @Override
    protected <T> Mono<T> executeCommand(
        String sql,
        Function<Statement, Mono<T>> body,
        BiFunction<String, Throwable, Mono<T>> exceptionHandler
    ) {
        return Mono.usingWhen(
            Mono.from(connectionFactory.create()).doOnNext(it -> it.setAutoCommit(true)),
            conn -> body.apply(conn.createStatement(sql)).onErrorResume(throwable -> exceptionHandler.apply(sql, throwable)),
            Connection::close,
            (connection, throwable) -> exceptionHandler.apply(sql, throwable),
            connection -> Mono.empty()
        );
    }

    @Override
    protected String toParameter(int index, String name) {
        if (MSSQL_NAME.equals(driver)) {
            return "@" + name;
        } else if (MYSQL_NAME.equals(driver)) {
            return "?";
        } else if (POSTGRES_NAME.equals(driver)) {
            return "$" + index;
        } else if (H2_NAME.equals(driver)) {
            return "$" + index;
        } else if (MARIA_NAME.equals(driver)) {
            return "?";
        } else if (ORACLE_NAME.equals(driver)) {
            return ":" + name;
        } else {
            return "$" + index;
        }
    }

    @Override
    protected Object toCompatibleDate(Instant date) {
        if (MSSQL_NAME.equals(driver)) {
            return LocalDateTime.ofInstant(date, ZoneId.systemDefault());
        } else if (MARIA_NAME.equals(driver)) {
            return LocalDateTime.ofInstant(date, ZoneId.systemDefault());
        } else if (ORACLE_NAME.equals(driver)) {
            return LocalDateTime.ofInstant(date, ZoneId.systemDefault());
        } else {
            return super.toCompatibleDate(date);
        }
    }
}
