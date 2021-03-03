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
package net.javacrumbs.shedlock.provider.cassandra;

import java.time.Instant;

class Lock {
    private final Instant lockUntil;
    private final Instant lockedAt;
    private final String lockedBy;

    Lock(Instant lockUntil, Instant lockedAt, String lockedBy) {
        this.lockUntil = lockUntil;
        this.lockedAt = lockedAt;
        this.lockedBy = lockedBy;
    }

    Instant getLockUntil() {
        return lockUntil;
    }

    Instant getLockedAt() {
        return lockedAt;
    }

    String getLockedBy() {
        return lockedBy;
    }
}
