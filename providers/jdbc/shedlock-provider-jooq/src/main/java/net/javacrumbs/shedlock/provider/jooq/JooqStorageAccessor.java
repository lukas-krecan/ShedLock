package net.javacrumbs.shedlock.provider.jooq;

import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.provider.jooq.Shedlock.SHEDLOCK;
import static org.jooq.impl.DSL.currentLocalDateTime;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.localDateTimeAdd;
import static org.jooq.impl.DSL.when;

import java.io.Serializable;
import java.sql.Connection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.NewTransactionRunner;
import net.javacrumbs.shedlock.support.NewTransactionRunner.TransactionCallback;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.TableField;
import org.jooq.TransactionalCallable;
import org.jooq.impl.DSL;
import org.jooq.types.DayToSecond;
import org.jspecify.annotations.Nullable;

class JooqStorageAccessor extends AbstractStorageAccessor {
    private final DSLContext dslContext;
    private final NewTransactionRunner newTransactionRunner;
    private final @Nullable DataSource dataSource;
    private final Shedlock t = SHEDLOCK;

    JooqStorageAccessor(DSLContext dslContext) {
        this(dslContext, TransactionCallback::execute);
    }

    JooqStorageAccessor(DSLContext dslContext, NewTransactionRunner newTransactionRunner) {
        this.dslContext = requireNonNull(dslContext, "dslContext can not be null");
        this.newTransactionRunner = requireNonNull(newTransactionRunner, "newTransactionRunner can not be null");
        this.dataSource = null;
    }

    /**
     * Runs every lock operation on a fresh connection obtained directly from {@code dataSource},
     * bypassing {@code dslContext}'s own connection entirely - see {@link
     * JooqLockProvider#JooqLockProvider(DSLContext, DataSource)}.
     */
    JooqStorageAccessor(DSLContext dslContext, DataSource dataSource) {
        this.dslContext = requireNonNull(dslContext, "dslContext can not be null");
        this.dataSource = requireNonNull(dataSource, "dataSource can not be null");
        this.newTransactionRunner = TransactionCallback::execute;
    }

    @Override
    public boolean insertRecord(LockConfiguration lockConfiguration) {
        return runInTransaction(tx -> tx.dsl()
                        .insertInto(t)
                        .set(data(lockConfiguration))
                        .onConflictDoNothing()
                        .execute()
                > 0);
    }

    @Override
    public boolean updateRecord(LockConfiguration lockConfiguration) {
        return runInTransaction(tx -> tx.dsl()
                        .update(t)
                        .set(data(lockConfiguration))
                        .where(t.NAME.eq(lockConfiguration.getName()).and(t.LOCK_UNTIL.le(now())))
                        .execute()
                > 0);
    }

    @Override
    public void unlock(LockConfiguration lockConfiguration) {
        Field<LocalDateTime> lockAtLeastFor =
                t.LOCKED_AT.add(DayToSecond.valueOf(lockConfiguration.getLockAtLeastFor()));
        runInTransaction(tx -> tx.dsl()
                .update(t)
                .set(
                        t.LOCK_UNTIL,
                        when(lockAtLeastFor.gt(now()), lockAtLeastFor).otherwise(now()))
                .where(t.NAME.eq(lockConfiguration.getName()).and(t.LOCKED_BY.eq(getHostname())))
                .execute());
    }

    @Override
    public boolean extend(LockConfiguration lockConfiguration) {
        return runInTransaction(tx -> tx.dsl()
                        .update(t)
                        .set(t.LOCK_UNTIL, nowPlus(lockConfiguration.getLockAtMostFor()))
                        .where(t.NAME.eq(lockConfiguration.getName())
                                .and(t.LOCKED_BY.eq(getHostname()))
                                .and(t.LOCK_UNTIL.gt(now())))
                        .execute()
                > 0);
    }

    private <T> T runInTransaction(TransactionalCallable<T> txCallable) {
        if (dataSource != null) {
            return runOnIndependentConnection(txCallable);
        }
        try {
            @SuppressWarnings("unchecked")
            T result = (T) newTransactionRunner.runInNewTransaction(() -> dslContext.transactionResult(txCallable));
            return result;
        } catch (Exception e) {
            throw new LockException(e);
        } catch (Throwable e) {
            throw new LockException(e);
        }
    }

    /**
     * Pulls a genuinely independent connection from {@code dataSource} and runs {@code
     * txCallable} there, never touching whatever connection {@code dslContext} itself is bound
     * to. This is what makes it safe even when {@code dslContext} is bound directly to a raw
     * {@link Connection} that may already have an ambient transaction (and other, unrelated
     * caller work) pending on it.
     */
    private <T> T runOnIndependentConnection(TransactionalCallable<T> txCallable) {
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            if (!originalAutoCommit) {
                connection.setAutoCommit(true);
            }
            try {
                DSLContext isolatedDsl = DSL.using(
                        connection,
                        dslContext.configuration().dialect(),
                        dslContext.configuration().settings());
                return isolatedDsl.transactionResult(txCallable);
            } finally {
                if (!originalAutoCommit) {
                    connection.setAutoCommit(false);
                }
            }
        } catch (Exception e) {
            throw new LockException(e);
        }
    }

    private Map<? extends TableField<Record, ? extends Serializable>, Serializable> data(
            LockConfiguration lockConfiguration) {
        return Map.of(
                t.NAME,
                lockConfiguration.getName(),
                t.LOCK_UNTIL,
                nowPlus(lockConfiguration.getLockAtMostFor()),
                t.LOCKED_AT,
                now(),
                t.LOCKED_BY,
                getHostname());
    }

    private Field<LocalDateTime> now() {
        return currentLocalDateTime(inline(6));
    }

    private Field<LocalDateTime> nowPlus(Duration duration) {
        return localDateTimeAdd(now(), DayToSecond.valueOf(duration));
    }
}
