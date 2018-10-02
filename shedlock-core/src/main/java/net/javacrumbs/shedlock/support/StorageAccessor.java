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

import net.javacrumbs.shedlock.core.LockConfiguration;

public interface StorageAccessor {
    /**
     * Inserts a record
     *
     * @param lockConfiguration LockConfiguration
     * @return true if inserted
     */
    boolean insertRecord(LockConfiguration lockConfiguration);

    /**
     * Updates record
     *
     * @param lockConfiguration LockConfiguration
     * @return true if updated
     */
    boolean updateRecord(LockConfiguration lockConfiguration);

    void unlock(LockConfiguration lockConfiguration);
}
