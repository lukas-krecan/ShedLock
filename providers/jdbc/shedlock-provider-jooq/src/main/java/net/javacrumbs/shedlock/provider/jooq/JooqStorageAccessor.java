package net.javacrumbs.shedlock.provider.jooq;

import static net.javacrumbs.shedlock.provider.jooq.Shedlock.SHEDLOCK;
import static org.jooq.impl.DSL.currentLocalDateTime;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.localDateTimeAdd;
import static org.jooq.impl.DSL.when;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.LockException;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.TableField;
import org.jooq.TransactionalCallable;
import org.jooq.types.DayToSecond;

class JooqStorageAccessor extends AbstractStorageAccessor {
    private final DSLContext dslContext;
    private final Shedlock t = SHEDLOCK;

    JooqStorageAccessor(DSLContext dslContext) {
        this.dslContext = dslContext;
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
        try {
            return dslContext.transactionResult(txCallable);
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
