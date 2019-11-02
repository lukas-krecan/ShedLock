/**
 * Copyright 2009-2019 the original author or authors.
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
import org.jetbrains.annotations.NotNull;

public interface StorageAccessor {
    /**
     * Inserts a record, if it does not already exists. If it exists, returns false.
     *
     * @param lockConfiguration LockConfiguration
     * @return true if inserted
     */
    boolean insertRecord(@NotNull LockConfiguration lockConfiguration);

    /**
     * Tries to update the lock record. If there is already a valid lock record (the lock is held by someone else)
     * update should not do anything and this method returns false.
     *
     * @param lockConfiguration LockConfiguration
     * @return true if updated
     */
    boolean updateRecord(@NotNull LockConfiguration lockConfiguration);

    void unlock(@NotNull LockConfiguration lockConfiguration);

    default boolean extend(@NotNull LockConfiguration lockConfiguration) {
        throw new UnsupportedOperationException();
    }
}
