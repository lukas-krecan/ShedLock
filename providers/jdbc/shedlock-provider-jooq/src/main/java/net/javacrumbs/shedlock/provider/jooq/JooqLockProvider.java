/**
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
package net.javacrumbs.shedlock.provider.jooq;

import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.types.DayToSecond;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static net.javacrumbs.shedlock.provider.jooq.Shedlock.SHEDLOCK;
import static net.javacrumbs.shedlock.support.Utils.getHostname;
import static org.jooq.impl.DSL.currentLocalDateTime;
import static org.jooq.impl.DSL.localDateTimeAdd;
import static org.jooq.impl.DSL.when;


public class JooqLockProvider implements ExtensibleLockProvider {
    private final DSLContext dslContext;

    public JooqLockProvider(@NonNull DSLContext dslContext) {

        this.dslContext = dslContext;
    }


    @Override
    @NonNull
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        var data = Map.of(
            SHEDLOCK.NAME, lockConfiguration.getName(),
            SHEDLOCK.LOCK_UNTIL, nowPlus(lockConfiguration.getLockAtMostFor()),
            SHEDLOCK.LOCKED_AT, now(),
            SHEDLOCK.LOCKED_BY, getHostname()
        );
        int rowsUpdated = dslContext.insertInto(SHEDLOCK)
            .set(data)
            .onDuplicateKeyUpdate()
            .set(data)
            .where(SHEDLOCK.LOCK_UNTIL.lessOrEqual(now()))
            .execute();
        if (rowsUpdated > 0) {
            return Optional.of(new JooqLock(dslContext, lockConfiguration));
        } else {
            return Optional.empty();
        }
    }

    private static Field<LocalDateTime> now() {
        return currentLocalDateTime();
    }

    private static Field<LocalDateTime> nowPlus(Duration duration) {
        return localDateTimeAdd(now(), DayToSecond.valueOf(duration));
    }

    private static class JooqLock extends AbstractSimpleLock {
        private final DSLContext dslContext;

        public JooqLock(DSLContext dslContext, LockConfiguration lockConfiguration) {
            super(lockConfiguration);
            this.dslContext = dslContext;
        }

        @Override
        protected void doUnlock() {
            Field<LocalDateTime> lockAtLeastFor = SHEDLOCK.LOCKED_AT.add(DayToSecond.valueOf(lockConfiguration.getLockAtLeastFor()));
            dslContext.update(SHEDLOCK).set(SHEDLOCK.LOCK_UNTIL, when(lockAtLeastFor.gt(now()), lockAtLeastFor).otherwise(now()))
                .where(SHEDLOCK.NAME.eq(lockConfiguration.getName()).and(SHEDLOCK.LOCKED_BY.eq(getHostname())))
                .execute();
        }

        @Override
        @NonNull
        protected Optional<SimpleLock> doExtend(LockConfiguration newConfiguration) {
              int rowsUpdated = dslContext.update(SHEDLOCK).set(SHEDLOCK.LOCK_UNTIL, nowPlus(newConfiguration.getLockAtMostFor()))
                .where(SHEDLOCK.NAME.eq(lockConfiguration.getName()).and(SHEDLOCK.LOCKED_BY.eq(getHostname())).and(SHEDLOCK.LOCK_UNTIL.gt(now())))
                .execute();
            if (rowsUpdated> 0) {
                return Optional.of(new JooqLock(dslContext, newConfiguration));
            } else {
                return Optional.empty();
            }
        }
    }
}
