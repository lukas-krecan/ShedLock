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
package net.javacrumbs.shedlock.support;

/**
 * Lock record registry that does not store anything.
 */
class DummyLockRecordRegistry implements LockRecordRegistry {
    @Override
    public void addLockRecord(String lockName) {}

    @Override
    public void removeLockRecord(String lockName) {}

    @Override
    public boolean lockRecordRecentlyCreated(String lockName) {
        return false;
    }

    @Override
    public void clear() {}
}
