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
package net.javacrumbs.shedlock.provider.consul;

import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.LockConfiguration;

class ConsulSimpleLock extends AbstractSimpleLock {
    private final ConsulLockProvider consulLockProvider;
    private final String sessionId;

    public ConsulSimpleLock(LockConfiguration lockConfiguration,
                            ConsulLockProvider consulLockProvider,
                            String sessionId) {
        super(lockConfiguration);
        this.consulLockProvider = consulLockProvider;
        this.sessionId = sessionId;
    }

    @Override
    protected void doUnlock() {
        consulLockProvider.unlock(sessionId, lockConfiguration);
    }
}
