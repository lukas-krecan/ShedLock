/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.ignite;

import static net.javacrumbs.shedlock.support.Utils.getHostname;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.apache.ignite.Ignite;
import org.apache.ignite.table.KeyValueView;
import org.apache.ignite.table.Table;
import org.apache.ignite.table.mapper.Mapper;

/**
 * Distributed lock using Apache Ignite.
 *
 * <p>
 * It uses a {@link String} key (lock name) and {@link LockValue} value.
 *
 * <p>
 * lockedAt and lockedBy are just for troubleshooting and are not read by the
 * code. Creating a lock:
 *
 * <ol>
 * <li>If there is no locks with given name, try to use
 * {@link KeyValueView#putIfAbsent}.
 * <li>If there is a lock with given name, and its lockUntil is before or equal
 * {@code now}, try to use {@link KeyValueView#replace}.
 * <li>Otherwise, return {@link Optional#empty}.
 * </ol>
 *
 * Extending a lock:
 *
 * <ol>
 * <li>If there is a lock with given name and hostname, and its lockUntil is
 * after {@code now}, try to use {@link KeyValueView#replace} to set lockUntil to
 * {@link LockConfiguration#getLockAtMostUntil}.
 * <li>Otherwise, return {@link Optional#empty}.
 * </ol>
 *
 * Unlock:
 *
 * <ol>
 * <li>If there is a lock with given name and hostname, try to use
 * {@link KeyValueView#replace} to set lockUntil to
 * {@link LockConfiguration#getUnlockTime}.
 * </ol>
 */
public class IgniteLockProvider implements ExtensibleLockProvider {
    /** Default ShedLock cache name. */
    public static final String DEFAULT_SHEDLOCK_CACHE_NAME = "shedLock";

    public static final ZoneId UTC = ZoneId.of("UTC");

    /** ShedLock key-value view. */
    private final KeyValueView<String, LockValue> keyValueView;

    /**
     * @param ignite
     *            Ignite instance.
     */
    public IgniteLockProvider(Ignite ignite) {
        this(ignite, DEFAULT_SHEDLOCK_CACHE_NAME);
    }

    /**
     * @param ignite
     *            Ignite instance.
     * @param shedLockTableName
     *            ShedLock table name to use instead of default.
     */
    public IgniteLockProvider(Ignite ignite, String shedLockTableName) {
        Table table = ignite.tables().table(shedLockTableName);
        if (table == null) {
            throw new IllegalArgumentException("Table '" + shedLockTableName + "' does not exist. "
                    + "Please create the table first or use the default table name.");
        }
        this.keyValueView = table.keyValueView(Mapper.of(String.class), Mapper.of(LockValue.class));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockCfg) {
        LocalDateTime now = now();
        String key = lockCfg.getName();

        LockValue newVal = new LockValue(now, toLocalDateTime(lockCfg.getLockAtMostUntil()), getHostname());
        LockValue oldVal = keyValueView.get(null, key);

        if (oldVal == null) {
            if (keyValueView.putIfAbsent(null, key, newVal)) return Optional.of(new IgniteLock(lockCfg, this));

            return Optional.empty();
        }

        if (!now.isBefore(oldVal.getLockUntil()) && keyValueView.replace(null, key, oldVal, newVal))
            return Optional.of(new IgniteLock(lockCfg, this));

        return Optional.empty();
    }

    /**
     * If there is a lock with given name and hostname, try to use
     * {@link KeyValueView#replace} to set lockUntil to
     * {@link LockConfiguration#getLockAtMostUntil}.
     *
     * @param lockCfg
     *            Lock configuration.
     * @return New lock if succeed extended. Empty, otherwise.
     */
    private Optional<SimpleLock> extend(LockConfiguration lockCfg) {
        LocalDateTime now = now();

        String key = lockCfg.getName();
        LockValue oldVal = keyValueView.get(null, key);

        if (oldVal == null
                || !oldVal.getLockedBy().equals(getHostname())
                || !oldVal.getLockUntil().isAfter(now)) return Optional.empty();

        LockValue newVal = oldVal.withLockUntil(toLocalDateTime(lockCfg.getLockAtMostUntil()));

        if (keyValueView.replace(null, key, oldVal, newVal)) return Optional.of(new IgniteLock(lockCfg, this));

        return Optional.empty();
    }

    private static LocalDateTime now() {
        return toLocalDateTime(Instant.now());
    }

    /**
     * If there is a lock with given name and hostname, try to use
     * {@link KeyValueView#replace} to set lockUntil to {@code now}.
     *
     * @param lockCfg
     *            Lock configuration.
     */
    private void unlock(LockConfiguration lockCfg) {
        String key = lockCfg.getName();
        LockValue oldVal = keyValueView.get(null, key);

        if (oldVal != null && oldVal.getLockedBy().equals(getHostname())) {
            LockValue newVal = oldVal.withLockUntil(toLocalDateTime(lockCfg.getUnlockTime()));

            keyValueView.replace(null, key, oldVal, newVal);
        }
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, UTC);
    }

    /** Ignite lock. */
    private static final class IgniteLock extends AbstractSimpleLock {
        /** Ignite lock provider. */
        private final IgniteLockProvider lockProvider;

        /**
         * @param lockCfg
         *            Lock configuration.
         * @param lockProvider
         *            Ignite lock provider.
         */
        private IgniteLock(LockConfiguration lockCfg, IgniteLockProvider lockProvider) {
            super(lockCfg);

            this.lockProvider = lockProvider;
        }

        /** {@inheritDoc} */
        @Override
        public void doUnlock() {
            lockProvider.unlock(lockConfiguration);
        }

        /** {@inheritDoc} */
        @Override
        public Optional<SimpleLock> doExtend(LockConfiguration newLockConfiguration) {
            return lockProvider.extend(newLockConfiguration);
        }
    }
}
