/**
 * Copyright 2009-2018 the original author or authors.
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
package net.javacrumbs.shedlock.support;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Some LockProviders have to decide if a new record has to be created or an old one updated.
 * This class helps them keep track of existing lock records, so they know if a lock record exists.
 */
class LockRecordRegistry {
    private final Set<String> lockRecords = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    public void addLockRecord(String lockName) {
        lockRecords.add(lockName);
    }

    public boolean lockRecordRecentlyCreated(String lockName) {
        return lockRecords.contains(lockName);
    }

    int getSize() {
        return lockRecords.size();
    }

    public void clear() {
        lockRecords.clear();
    }
}
