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

    JooqStorageAccessor(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    @Override
    public boolean insertRecord(@NonNull LockConfiguration lockConfiguration) {
        return dslContext.insertInto(SHEDLOCK)
            .set(getData(lockConfiguration))
            .onConflictDoNothing()
            .execute() > 0;
    }

    @Override
    public boolean updateRecord(@NonNull LockConfiguration lockConfiguration) {
        return dslContext.update(SHEDLOCK)
            .set(getData(lockConfiguration))
            .where(SHEDLOCK.NAME.eq(lockConfiguration.getName()).and(SHEDLOCK.LOCK_UNTIL.le(now())))
            .execute() > 0;
    }

    @Override
    public void unlock(LockConfiguration lockConfiguration) {
        Field<LocalDateTime> lockAtLeastFor = SHEDLOCK.LOCKED_AT.add(DayToSecond.valueOf(lockConfiguration.getLockAtLeastFor()));
        dslContext.update(SHEDLOCK).set(SHEDLOCK.LOCK_UNTIL, when(lockAtLeastFor.gt(now()), lockAtLeastFor).otherwise(now()))
            .where(SHEDLOCK.NAME.eq(lockConfiguration.getName()).and(SHEDLOCK.LOCKED_BY.eq(getHostname())))
            .execute();
    }

    @Override
    public boolean extend(LockConfiguration lockConfiguration) {
        return dslContext.update(SHEDLOCK).set(SHEDLOCK.LOCK_UNTIL, nowPlus(lockConfiguration.getLockAtMostFor()))
            .where(SHEDLOCK.NAME.eq(lockConfiguration.getName()).and(SHEDLOCK.LOCKED_BY.eq(getHostname())).and(SHEDLOCK.LOCK_UNTIL.gt(now())))
            .execute() > 0;
    }


    private Map<? extends TableField<Record, ? extends Serializable>, Serializable> getData(LockConfiguration lockConfiguration) {
        return Map.of(
            SHEDLOCK.NAME, lockConfiguration.getName(),
            SHEDLOCK.LOCK_UNTIL, nowPlus(lockConfiguration.getLockAtMostFor()),
            SHEDLOCK.LOCKED_AT, now(),
            SHEDLOCK.LOCKED_BY, getHostname()
        );
    }

    private Field<LocalDateTime> now() {
        return currentLocalDateTime(inline(6));
    }

    private Field<LocalDateTime> nowPlus(Duration duration) {
        return localDateTimeAdd(now(), DayToSecond.valueOf(duration));
    }
}
