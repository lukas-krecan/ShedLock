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

import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import io.r2dbc.spi.Statement;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Internal class, please do not use.
 */
abstract class AbstractR2dbcStorageAccessor extends AbstractStorageAccessor {
    private final String tableName;

    public AbstractR2dbcStorageAccessor(@NonNull String tableName) {
        this.tableName = requireNonNull(tableName, "tableName can not be null");
    }

    @Override
    public boolean insertRecord(@NonNull LockConfiguration lockConfiguration) {
        return Boolean.TRUE.equals(Mono.from(insertRecordReactive(lockConfiguration)).block());
    }

    @Override
    public boolean updateRecord(@NonNull LockConfiguration lockConfiguration) {
        return Boolean.TRUE.equals(Mono.from(updateRecordReactive(lockConfiguration)).block());
    }

    @Override
    public boolean extend(@NonNull LockConfiguration lockConfiguration) {
        return Boolean.TRUE.equals(Mono.from(extendReactive(lockConfiguration)).block());
    }

    @Override
    public void unlock(@NonNull LockConfiguration lockConfiguration) {
        Mono.from(unlockReactive(lockConfiguration)).block();
    }

    public Publisher<Boolean> insertRecordReactive(@NonNull LockConfiguration lockConfiguration) {
        // Try to insert if the record does not exists (not optimal, but the simplest platform agnostic way)
        String sql = "INSERT INTO " + tableName + "(name, lock_until, locked_at, locked_by) VALUES(" + toParameter(1, "name") + ", " + toParameter(2, "lock_until") + ", " + toParameter(3, "locked_at") + ", " + toParameter(4, "locked_by") + ")";
        return executeCommand(sql, statement -> {
            statement.bind(0, lockConfiguration.getName());
            statement.bind(1, toCompatibleDate(lockConfiguration.getLockAtMostUntil()));
            statement.bind(2, toCompatibleDate(ClockProvider.now()));
            statement.bind(3, getHostname());
            return Mono.from(statement.execute()).flatMap(it -> Mono.from(it.getRowsUpdated())).map(it -> it > 0);
        }, this::handleInsertionException);
    }

    public Publisher<Boolean> updateRecordReactive(@NonNull LockConfiguration lockConfiguration) {
        String sql = "UPDATE " + tableName + " SET lock_until = " + toParameter(1, "lock_until") + ", locked_at = " + toParameter(2, "locked_at") + ", locked_by = " + toParameter(3, "locked_by") + " WHERE name = " + toParameter(4, "name") + " AND lock_until <= " + toParameter(5, "now");
        return executeCommand(sql, statement -> {
            Instant now = ClockProvider.now();
            statement.bind(0, toCompatibleDate(lockConfiguration.getLockAtMostUntil()));
            statement.bind(1, toCompatibleDate(now));
            statement.bind(2, getHostname());
            statement.bind(3, lockConfiguration.getName());
            statement.bind(4, toCompatibleDate(now));
            return Mono.from(statement.execute()).flatMap(it -> Mono.from(it.getRowsUpdated())).map(it -> it > 0);
        }, this::handleUpdateException);
    }

    public Publisher<Boolean> extendReactive(@NonNull LockConfiguration lockConfiguration) {
        String sql = "UPDATE " + tableName + " SET lock_until = " + toParameter(1, "lock_until") + " WHERE name = " + toParameter(2, "name") + " AND locked_by = " + toParameter(3, "locked_by") + " AND lock_until > " + toParameter(4, "now");

        logger.debug("Extending lock={} until={}", lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());

        return executeCommand(sql, statement -> {
            statement.bind(0, toCompatibleDate(lockConfiguration.getLockAtMostUntil()));
            statement.bind(1, lockConfiguration.getName());
            statement.bind(2, getHostname());
            statement.bind(3, toCompatibleDate(ClockProvider.now()));
            return Mono.from(statement.execute()).flatMap(it -> Mono.from(it.getRowsUpdated())).map(it -> it > 0);
        }, this::handleUnlockException);
    }

    public Publisher<Void> unlockReactive(@NonNull LockConfiguration lockConfiguration) {
        String sql = "UPDATE " + tableName + " SET lock_until = " + toParameter(1, "lock_until") + " WHERE name = " + toParameter(2, "name");
        return executeCommand(sql, statement -> {
            statement.bind(0, toCompatibleDate(lockConfiguration.getUnlockTime()));
            statement.bind(1, lockConfiguration.getName());
            return Mono.from(statement.execute()).flatMap(it -> Mono.from(it.getRowsUpdated())).then();
        }, (s, t) -> handleUnlockException(s, t).then());
    }

    protected abstract <T> Mono<T> executeCommand(
        String sql,
        Function<Statement, Mono<T>> body,
        BiFunction<String, Throwable, Mono<T>> exceptionHandler
    );

    protected abstract Object toCompatibleDate(Instant date);

    protected abstract String toParameter(int index, String name);

    Mono<Boolean> handleInsertionException(String sql, Throwable e) {
        if (e instanceof R2dbcDataIntegrityViolationException) {
            // lock record already exists
        } else {
            // can not throw exception here, some drivers (Postgres) do not throw SQLIntegrityConstraintViolationException on duplicate key
            // we will try update in the next step, su if there is another problem, an exception will be thrown there
            logger.debug("Exception thrown when inserting record", e);
        }
        return Mono.just(false);
    }

    Mono<Boolean> handleUpdateException(String sql, Throwable e) {
        return Mono.error(new LockException("Unexpected exception when locking", e));
    }

    Mono<Boolean> handleUnlockException(String sql, Throwable e) {
        return Mono.error(new LockException("Unexpected exception when unlocking", e));
    }
}
