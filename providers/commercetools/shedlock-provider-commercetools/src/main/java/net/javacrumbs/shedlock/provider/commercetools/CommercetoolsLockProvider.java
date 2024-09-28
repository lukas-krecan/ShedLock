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

import com.commercetools.api.client.ProjectApiRoot;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;

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
 *      "lockedAt": 1.727437877759E9
 *      "lockUntil": 1.727437878009E9,
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
public class CommercetoolsLockProvider extends StorageBasedLockProvider {

    public CommercetoolsLockProvider(ProjectApiRoot projectApiRoot) {
        super(new CommercetoolsStorageAccessor(projectApiRoot));
    }
}
