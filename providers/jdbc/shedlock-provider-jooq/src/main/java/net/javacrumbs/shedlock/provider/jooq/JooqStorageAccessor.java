package net.javacrumbs.shedlock.provider.jooq;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.TableField;
import org.jooq.types.DayToSecond;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static net.javacrumbs.shedlock.provider.jooq.Shedlock.SHEDLOCK;
import static org.jooq.impl.DSL.currentLocalDateTime;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.localDateTimeAdd;
import static org.jooq.impl.DSL.when;

class JooqStorageAccessor extends AbstractStorageAccessor {
    private final DSLContext dslContext;
    private final Shedlock t = SHEDLOCK;

    JooqStorageAccessor(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    @Override
    public boolean insertRecord(@NonNull LockConfiguration lockConfiguration) {
        return dslContext.transactionResult(tx -> tx.dsl().insertInto(t)
            .set(data(lockConfiguration))
            .onConflictDoNothing()
            .execute() > 0);
    }

    @Override
    public boolean updateRecord(@NonNull LockConfiguration lockConfiguration) {
        return dslContext.transactionResult(tx -> tx.dsl().update(t)
            .set(data(lockConfiguration))
            .where(t.NAME.eq(lockConfiguration.getName()).and(t.LOCK_UNTIL.le(now())))
            .execute() > 0);
    }

    @Override
    public void unlock(LockConfiguration lockConfiguration) {
        Field<LocalDateTime> lockAtLeastFor = t.LOCKED_AT.add(DayToSecond.valueOf(lockConfiguration.getLockAtLeastFor()));
        dslContext.transaction(tx -> tx.dsl().update(t).set(t.LOCK_UNTIL, when(lockAtLeastFor.gt(now()), lockAtLeastFor).otherwise(now()))
            .where(t.NAME.eq(lockConfiguration.getName()).and(t.LOCKED_BY.eq(getHostname())))
            .execute());
    }

    @Override
    public boolean extend(@NonNull LockConfiguration lockConfiguration) {
        return dslContext.transactionResult(tx -> tx.dsl().update(t).set(t.LOCK_UNTIL, nowPlus(lockConfiguration.getLockAtMostFor()))
            .where(t.NAME.eq(lockConfiguration.getName()).and(t.LOCKED_BY.eq(getHostname())).and(t.LOCK_UNTIL.gt(now())))
            .execute() > 0);
    }


    private Map<? extends TableField<Record, ? extends Serializable>, Serializable> data(LockConfiguration lockConfiguration) {
        return Map.of(
            t.NAME, lockConfiguration.getName(),
            t.LOCK_UNTIL, nowPlus(lockConfiguration.getLockAtMostFor()),
            t.LOCKED_AT, now(),
            t.LOCKED_BY, getHostname()
        );
    }

    private Field<LocalDateTime> now() {
        return currentLocalDateTime(inline(6));
    }

    private Field<LocalDateTime> nowPlus(Duration duration) {
        return localDateTimeAdd(now(), DayToSecond.valueOf(duration));
    }
}
