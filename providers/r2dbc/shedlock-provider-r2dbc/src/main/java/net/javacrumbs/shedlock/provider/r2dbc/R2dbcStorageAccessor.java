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

import static java.util.regex.Matcher.quoteReplacement;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import io.r2dbc.spi.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.sql.SqlStatementsSource;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.LockException;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

class R2dbcStorageAccessor extends AbstractStorageAccessor {
    private static final Pattern NAMED_PARAMETER_PATTERN = Pattern.compile(":[a-zA-Z]+");

    private final ConnectionFactory connectionFactory;
    private final SqlStatementsSource sqlStatementsSource;
    private final R2dbcAdapter adapter;

    R2dbcStorageAccessor(R2dbcLockProvider.Configuration configuration) {
        this.connectionFactory = configuration.getConnectionFactory();
        this.sqlStatementsSource = SqlStatementsSource.create(configuration);
        this.adapter = R2dbcAdapter.create(configuration.getDatabaseProduct());
    }

    protected String toParameter(int index, String name) {
        return adapter.toParameter(index, name);
    }

    protected void bind(Statement statement, int index, String name, Object value) {
        adapter.bind(statement, index, name, value);
    }

    @Override
    public boolean insertRecord(LockConfiguration lockConfiguration) {
        return Boolean.TRUE.equals(block(insertRecordReactive(lockConfiguration)));
    }

    @Override
    public boolean updateRecord(LockConfiguration lockConfiguration) {
        return Boolean.TRUE.equals(block(updateRecordReactive(lockConfiguration)));
    }

    @Override
    public boolean extend(LockConfiguration lockConfiguration) {
        return Boolean.TRUE.equals(block(extendReactive(lockConfiguration)));
    }

    @Override
    public void unlock(LockConfiguration lockConfiguration) {
        block(unlockReactive(lockConfiguration));
    }

    private <T> @Nullable T block(Mono<T> mono) {
        try {
            return mono.block(Duration.ofSeconds(30));
        } catch (Exception e) {
            if (e instanceof LockException lockException) {
                throw lockException;
            }
            throw new LockException("Unexpected exception when executing r2dbc operation", e);
        }
    }

    Mono<Boolean> insertRecordReactive(LockConfiguration lockConfiguration) {
        // Try to insert if the record does not exist (not optimal, but the simplest
        // platform agnostic
        // way)
        var sqlStatement =
                translate(sqlStatementsSource.getInsertStatement(), sqlStatementsSource.params(lockConfiguration));
        return executeCommand(sqlStatement, this::handleInsertionException);
    }

    Mono<Boolean> updateRecordReactive(LockConfiguration lockConfiguration) {
        var sqlStatement =
                translate(sqlStatementsSource.getUpdateStatement(), sqlStatementsSource.params(lockConfiguration));
        return executeCommand(sqlStatement, this::handleUpdateException);
    }

    Mono<Boolean> extendReactive(LockConfiguration lockConfiguration) {
        var sqlStatement =
                translate(sqlStatementsSource.getExtendStatement(), sqlStatementsSource.params(lockConfiguration));

        logger.debug("Extending lock={} until={}", lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());

        return executeCommand(sqlStatement, this::handleUnlockException);
    }

    Mono<Boolean> unlockReactive(LockConfiguration lockConfiguration) {
        var sqlStatement =
                translate(sqlStatementsSource.getUnlockStatement(), sqlStatementsSource.params(lockConfiguration));
        return executeCommand(sqlStatement, this::handleUnlockException);
    }

    private Mono<Boolean> executeCommand(
            SqlStatement sqlStatement, BiFunction<String, Throwable, Mono<Boolean>> exceptionHandler) {
        return Mono.usingWhen(
                Mono.from(connectionFactory.create()).doOnNext(it -> it.setAutoCommit(true)),
                conn -> {
                    Statement statement = conn.createStatement(sqlStatement.sql);
                    for (int i = 0; i < sqlStatement.parameters.size(); i++) {
                        SqlParam param = sqlStatement.parameters.get(i);
                        bind(statement, i, param.name(), param.value());
                    }
                    return Mono.from(statement.execute())
                            .flatMap(it -> Mono.from(it.getRowsUpdated()))
                            .map(it -> it > 0)
                            .onErrorResume(throwable -> exceptionHandler.apply(sqlStatement.sql, throwable));
                },
                Connection::close,
                (connection, throwable) -> Mono.from(connection.close()),
                connection -> Mono.from(connection.close()).then());
    }

    Mono<Boolean> handleInsertionException(String sql, Throwable e) {
        if (e instanceof R2dbcDataIntegrityViolationException) {
            // lock record already exists
            return Mono.just(false);
        } else {
            return Mono.error(new LockException("Unexpected exception when locking", e));
        }
    }

    private SqlStatement translate(String statement, Map<String, Object> namedParameters) {
        List<SqlParam> parameters = new ArrayList<>();
        AtomicInteger index = new AtomicInteger(1);
        var translatedSql = NAMED_PARAMETER_PATTERN.matcher(statement).replaceAll(result -> {
            String key = result.group().substring(1);
            if (!namedParameters.containsKey(key)) {
                throw new IllegalStateException("Parameter " + key + " not found");
            }
            parameters.add(new SqlParam(key, namedParameters.get(key)));
            return quoteReplacement(toParameter(index.getAndIncrement(), key));
        });
        return new SqlStatement(translatedSql, parameters);
    }

    Mono<Boolean> handleUpdateException(String sql, Throwable e) {
        return Mono.error(new LockException("Unexpected exception when locking", e));
    }

    Mono<Boolean> handleUnlockException(String sql, Throwable e) {
        return Mono.error(new LockException("Unexpected exception when unlocking", e));
    }

    private record SqlParam(String name, Object value) {}

    private record SqlStatement(String sql, List<SqlParam> parameters) {}
}
