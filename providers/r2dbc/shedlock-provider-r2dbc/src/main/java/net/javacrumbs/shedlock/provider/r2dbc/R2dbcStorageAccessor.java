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

import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

class R2dbcStorageAccessor extends AbstractR2dbcStorageAccessor {

    private final ConnectionFactory connectionFactory;
    private R2dbcAdapter adapter;

    R2dbcStorageAccessor(@NonNull ConnectionFactory connectionFactory, @NonNull String tableName) {
        super(tableName);
        this.connectionFactory = requireNonNull(connectionFactory, "dataSource can not be null");
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
            (connection, throwable) -> Mono.from(connection.close()).then(exceptionHandler.apply(sql, throwable)),
            connection -> Mono.from(connection.close()).then()
        );
    }

    @Override
    protected String toParameter(int index, String name) {
       return getAdapter().toParameter(index, name);
    }

    @Override
    protected void bind(Statement statement, int index, String name, Object value) {
        getAdapter().bind(statement, index, name, value);
    }

    private R2dbcAdapter getAdapter() {
        synchronized (this) {
            if (adapter == null) {
                adapter = R2dbcAdapter.create(connectionFactory.getMetadata().getName());
            }
            return adapter;
        }
    }
}
