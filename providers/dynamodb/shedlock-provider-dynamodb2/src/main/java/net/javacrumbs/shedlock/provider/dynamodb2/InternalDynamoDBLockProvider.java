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
package net.javacrumbs.shedlock.provider.dynamodb2;

import static net.javacrumbs.shedlock.core.ClockProvider.now;

import java.util.Optional;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.Utils;

class InternalDynamoDBLockProvider implements LockProvider {
    private final String hostname;
    private final DynamoDBLockRepository repository;

    InternalDynamoDBLockProvider(DynamoDBLockRepository repository) {
        this.repository = repository;
        this.hostname = Utils.getHostname();
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        try {
            if (repository.obtainLock(
                    lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil(), now(), hostname)) {
                return Optional.of(new DynamoDBLock(repository, lockConfiguration));
            }
            return Optional.empty();
        } catch (Exception e) {
            throw new LockException("Error on lock", e);
        }
    }

    private static final class DynamoDBLock extends AbstractSimpleLock {
        private final DynamoDBLockRepository repository;

        private DynamoDBLock(DynamoDBLockRepository repository, LockConfiguration lockConfiguration) {
            super(lockConfiguration);
            this.repository = repository;
        }

        @Override
        public void doUnlock() {
            // Set lockUntil to now or lockAtLeastUntil whichever is later
            repository.releaseLock(lockConfiguration.getName(), lockConfiguration.getUnlockTime());
        }
    }
}
