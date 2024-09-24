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
package net.javacrumbs.shedlock.provider.commercetools;

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static net.javacrumbs.shedlock.support.Utils.getHostname;

import com.commercetools.api.client.ProjectApiRoot;
import com.commercetools.api.models.custom_object.CustomObject;
import com.commercetools.api.models.custom_object.CustomObjectDraft;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vrap.rmf.base.client.ApiHttpException;
import io.vrap.rmf.base.client.DeserializationException;
import io.vrap.rmf.base.client.error.NotFoundException;
import io.vrap.rmf.base.client.utils.json.JsonUtils;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.Optional;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;

/**
 * Lock using commercetools. Requires a commercetools api client.
 * <p>
 * Locks are written to a custom object with the following document structure:
 *
 * <pre>
 * {
 *    "container" : "lock",
 *    "key" : "lockName",
 *    "value" :  {
 *      "lockedAt": "2024-09-21T19:00:04.071Z"
 *      "lockUntil": "2024-09-21T19:15:04.071Z",
 *      "lockedBy": "hostname"
 *    }
 * }
 * </pre>
 *
 * <p>
 * lockedAt and lockedBy are just for troubleshooting and are not read by the
 * code
 *
 * <ol>
 * <li>Attempts to insert a new lock value.
 * <li>We will try to create or update a custom object for the lock. The lock name is used as the key.
 * <li>If there is no existing lock object or a lock object exists but has timeout out (lockUntil < now), we have the lock.
 * <li>When unlocking, lockUntil is set to the unlock time.
 * </ol>
 */
public class CommercetoolsLockProvider implements LockProvider {
    static final String LOCK_CONTAINER = "lock";

    private final ProjectApiRoot projectApiRoot;
    private final String hostname;
    private final ObjectMapper objectMapper;

    public CommercetoolsLockProvider(ProjectApiRoot projectApiRoot) {
        this.projectApiRoot = projectApiRoot;
        this.hostname = getHostname();
        this.objectMapper = JsonUtils.createObjectMapper();
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        try {
            Instant now = now();
            LockValue oldVal = getExistingLock(lockConfiguration);
            LockValue newVal = new LockValue(now, lockConfiguration.getLockAtMostUntil(), hostname);
            if (oldVal == null || !now.isBefore(oldVal.lockUntil())) {
                createOrUpdateLockValue(lockConfiguration.getName(), newVal);
                return Optional.of(new CommercetoolsSimpleLock(lockConfiguration));
            }
            return Optional.empty();
        } catch (ConcurrentModificationException e) {
            return Optional.empty();
        } catch (ApiHttpException e) {
            throw new LockException("Unexpected exception occurred", e);
        }
    }

    private LockValue getExistingLock(LockConfiguration lockConfiguration) {
        try {
            CustomObject responseBody = projectApiRoot
                .customObjects()
                .withContainerAndKey(LOCK_CONTAINER, lockConfiguration.getName())
                .get()
                .executeBlocking()
                .getBody();
            return objectMapper.convertValue(responseBody.getValue(), LockValue.class);
        } catch (NotFoundException | DeserializationException ex) {
            return null;
        }
    }

    private void createOrUpdateLockValue(String lockName, LockValue lockValue) {
        CustomObjectDraft customObjectDraft = CustomObjectDraft.builder()
            .container(LOCK_CONTAINER)
            .key(lockName)
            .value(lockValue)
            .build();
        projectApiRoot.customObjects().post(customObjectDraft).executeBlocking();
    }

    private final class CommercetoolsSimpleLock extends AbstractSimpleLock {

        private CommercetoolsSimpleLock(LockConfiguration lockConfiguration) {
            super(lockConfiguration);
        }

        @Override
        public void doUnlock() {
            try {
                LockValue existingLockValue = getExistingLock(lockConfiguration);
                if (existingLockValue != null) {
                    createOrUpdateLockValue(
                        lockConfiguration.getName(),
                        new LockValue(existingLockValue.lockedAt(), lockConfiguration.getUnlockTime(), hostname));
                } else {
                    throw new LockException("Existing lock does not exist");
                }

            } catch (ApiHttpException e) {
                throw new LockException("Unexpected exception occurred", e);
            }
        }
    }
}
