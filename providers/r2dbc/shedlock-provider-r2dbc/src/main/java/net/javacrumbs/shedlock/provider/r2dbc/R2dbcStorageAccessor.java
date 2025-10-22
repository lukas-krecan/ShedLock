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

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableMap;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import io.r2dbc.spi.Statement;
import java.time.Duration;
import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
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

    @Nullable
    private R2dbcAdapter adapter;

    R2dbcStorageAccessor(R2dbcLockProvider.Configuration configuration) {
        this.connectionFactory = configuration.getConnectionFactory();
        this.sqlStatementsSource = SqlStatementsSource.create(configuration);
    }

    protected <T> Mono<T> executeCommand(
            String sql, Function<Statement, Mono<T>> body, BiFunction<String, Throwable, Mono<T>> exceptionHandler) {
        return Mono.usingWhen(
                Mono.from(connectionFactory.create()).doOnNext(it -> it.setAutoCommit(true)),
                conn -> body.apply(conn.createStatement(sql))
                        .onErrorResume(throwable -> exceptionHandler.apply(sql, throwable)),
                Connection::close,
                (connection, throwable) -> Mono.from(connection.close()),
                connection -> Mono.from(connection.close()).then());
    }

    protected String toParameter(int index, String name) {
        return getAdapter().toParameter(index, name);
    }

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

    @Override
    public boolean insertRecord(LockConfiguration lockConfiguration) {
        String stmt = sqlStatementsSource().getInsertStatement();
        try {
            return Boolean.TRUE.equals(block(executeUpdate(stmt, lockConfiguration)));
        } catch (Exception e) {
            Throwable cause = unwrap(e);
            if (cause instanceof R2dbcDataIntegrityViolationException) {
                return false;
            }
            logger.debug("Exception thrown when inserting record", cause);
            throw new LockException("Unexpected exception when locking", cause);
        }
    }

    @Override
    public boolean updateRecord(LockConfiguration lockConfiguration) {
        String stmt = sqlStatementsSource().getUpdateStatement();
        try {
            return Boolean.TRUE.equals(block(executeUpdate(stmt, lockConfiguration)));
        } catch (Exception e) {
            logger.debug("Unexpected exception when updating lock record", e);
            throw new LockException("Unexpected exception when locking", unwrap(e));
        }
    }

    @Override
    public boolean extend(LockConfiguration lockConfiguration) {
        String stmt = sqlStatementsSource().getExtendStatement();
        logger.debug("Extending lock={} until={}", lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());
        try {
            return Boolean.TRUE.equals(block(executeUpdate(stmt, lockConfiguration)));
        } catch (Exception e) {
            throw new LockException("Unexpected exception when extending", unwrap(e));
        }
    }

    @Override
    public void unlock(LockConfiguration lockConfiguration) {
        String stmt = sqlStatementsSource().getUnlockStatement();
        try {
            block(executeUpdate(stmt, lockConfiguration));
        } catch (Exception e) {
            throw new LockException("Unexpected exception when unlocking", unwrap(e));
        }
    }

    private String translate(String statement, java.util.List<String> parameterNames) {
        AtomicInteger index = new AtomicInteger(1);
        return NAMED_PARAMETER_PATTERN.matcher(statement).replaceAll(result -> {
            String paramName = result.group().substring(1);
            parameterNames.add(paramName);
            return java.util.regex.Matcher.quoteReplacement(toParameter(index.getAndIncrement(), paramName));
        });
    }

    private SqlStatementsSource sqlStatementsSource() {
        return sqlStatementsSource;
    }

    private Mono<Boolean> executeUpdate(String sql, LockConfiguration lockConfiguration) {
        Map<String, Object> params = translateParams(sqlStatementsSource().params(lockConfiguration));
        java.util.List<String> parameterNames = new java.util.ArrayList<>();
        String translatedSql = translate(sql, parameterNames);
        return executeCommand(
                translatedSql,
                statement -> {
                    bindParams(statement, params, parameterNames);
                    return Mono.from(statement.execute())
                            .flatMap(it -> Mono.from(it.getRowsUpdated()))
                            .map(it -> it > 0);
                },
                (s, t) -> Mono.error(new LockException("Unexpected exception when executing SQL", t)));
    }

    private void bindParams(Statement statement, Map<String, Object> params, java.util.List<String> parameterNames) {
        AtomicInteger index = new AtomicInteger(0);
        parameterNames.forEach(
                name -> bind(statement, index.getAndIncrement(), name, requireNonNull(params.get(name))));
    }

    private Map<String, Object> translateParams(Map<String, Object> params) {
        return params.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), translate(entry.getValue())))
                .collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Object translate(Object value) {
        if (value instanceof Calendar cal) {
            return cal.toInstant();
        } else {
            return value;
        }
    }

    private static Throwable unwrap(Throwable e) {
        if (e.getCause() != null && e.getCause() instanceof LockException) {
            return e.getCause();
        }
        return e;
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
}
