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

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import io.r2dbc.spi.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.LockException;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

class R2dbcStorageAccessor extends AbstractStorageAccessor {

    private final ConnectionFactory connectionFactory;
    private final String tableName;

    @Nullable
    private R2dbcAdapter adapter;

    R2dbcStorageAccessor(ConnectionFactory connectionFactory, String tableName) {
        this.tableName = requireNonNull(tableName, "tableName can not be null");
        this.connectionFactory = requireNonNull(connectionFactory, "dataSource can not be null");
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
        String sql = "INSERT INTO " + tableName + "(name, lock_until, locked_at, locked_by) VALUES("
                + toParameter(1, "name") + ", " + toParameter(2, "lock_until") + ", " + toParameter(3, "locked_at")
                + ", " + toParameter(4, "locked_by") + ")";
        return executeCommand(
                sql,
                statement -> {
                    bind(statement, 0, "name", lockConfiguration.getName());
                    bind(statement, 1, "lock_until", lockConfiguration.getLockAtMostUntil());
                    bind(statement, 2, "locked_at", ClockProvider.now());
                    bind(statement, 3, "locked_by", getHostname());
                    return Mono.from(statement.execute())
                            .flatMap(it -> Mono.from(it.getRowsUpdated()))
                            .map(it -> it > 0);
                },
                this::handleInsertionException);
    }

    Mono<Boolean> updateRecordReactive(LockConfiguration lockConfiguration) {
        String sql = "UPDATE " + tableName + " SET lock_until = " + toParameter(1, "lock_until") + ", locked_at = "
                + toParameter(2, "locked_at") + ", locked_by = " + toParameter(3, "locked_by") + " WHERE name = "
                + toParameter(4, "name") + " AND lock_until <= " + toParameter(5, "now");
        return executeCommand(
                sql,
                statement -> {
                    Instant now = ClockProvider.now();
                    bind(statement, 0, "lock_until", lockConfiguration.getLockAtMostUntil());
                    bind(statement, 1, "locked_at", now);
                    bind(statement, 2, "locked_by", getHostname());
                    bind(statement, 3, "name", lockConfiguration.getName());
                    bind(statement, 4, "now", now);
                    return Mono.from(statement.execute())
                            .flatMap(it -> Mono.from(it.getRowsUpdated()))
                            .map(it -> it > 0);
                },
                this::handleUpdateException);
    }

    Mono<Boolean> extendReactive(LockConfiguration lockConfiguration) {
        String sql = "UPDATE " + tableName + " SET lock_until = " + toParameter(1, "lock_until") + " WHERE name = "
                + toParameter(2, "name") + " AND locked_by = " + toParameter(3, "locked_by") + " AND lock_until > "
                + toParameter(4, "now");

        logger.debug("Extending lock={} until={}", lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());

        return executeCommand(
                sql,
                statement -> {
                    bind(statement, 0, "lock_until", lockConfiguration.getLockAtMostUntil());
                    bind(statement, 1, "name", lockConfiguration.getName());
                    bind(statement, 2, "locked_by", getHostname());
                    bind(statement, 3, "now", ClockProvider.now());
                    return Mono.from(statement.execute())
                            .flatMap(it -> Mono.from(it.getRowsUpdated()))
                            .map(it -> it > 0);
                },
                this::handleUnlockException);
    }

    Mono<Void> unlockReactive(LockConfiguration lockConfiguration) {
        String sql = "UPDATE " + tableName + " SET lock_until = " + toParameter(1, "lock_until") + " WHERE name = "
                + toParameter(2, "name");
        return executeCommand(
                sql,
                statement -> {
                    bind(statement, 0, "lock_until", lockConfiguration.getUnlockTime());
                    bind(statement, 1, "name", lockConfiguration.getName());
                    return Mono.from(statement.execute())
                            .flatMap(it -> Mono.from(it.getRowsUpdated()))
                            .then();
                },
                (s, t) -> handleUnlockException(s, t).then());
    }

    Mono<Boolean> handleInsertionException(String sql, Throwable e) {
        if (e instanceof R2dbcDataIntegrityViolationException) {
            // lock record already exists
            return Mono.just(false);
        } else {
            return Mono.error(new LockException("Unexpected exception when locking", e));
        }
    }

    Mono<Boolean> handleUpdateException(String sql, Throwable e) {
        return Mono.error(new LockException("Unexpected exception when locking", e));
    }

    Mono<Boolean> handleUnlockException(String sql, Throwable e) {
        return Mono.error(new LockException("Unexpected exception when unlocking", e));
    }
}
