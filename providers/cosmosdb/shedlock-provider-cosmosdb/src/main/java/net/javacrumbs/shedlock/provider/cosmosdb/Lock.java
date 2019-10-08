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
package net.javacrumbs.shedlock.provider.cosmosdb;

import java.util.Date;
import java.util.Objects;

public class Lock {

    private String id;
    private Date lockUntil;
    private Date lockedAt;
    private String lockedBy;
    private String lockGroup;

    Lock() {
        //required from CosmosDB
    }

    Lock(String id, Date lockUntil, Date lockedAt, String lockedBy, String lockGroup) {
        this.id = id;
        this.lockUntil = lockUntil;
        this.lockedAt = lockedAt;
        this.lockedBy = lockedBy;
        this.lockGroup = lockGroup;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(Date lockedAt) {
        this.lockedAt = lockedAt;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(String lockedBy) {
        this.lockedBy = lockedBy;
    }

    public Date getLockUntil() {
        return lockUntil;
    }

    public void setLockUntil(Date lockUntil) {
        this.lockUntil = lockUntil;
    }

    public String getLockGroup() {
        return lockGroup;
    }

    public void setLockGroup(String lockGroup) {
        this.lockGroup = lockGroup;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Lock lock = (Lock) o;
        return Objects.equals(id, lock.id) &&
                Objects.equals(lockUntil, lock.lockUntil) &&
                Objects.equals(lockedAt, lock.lockedAt) &&
                Objects.equals(lockedBy, lock.lockedBy) &&
                Objects.equals(lockGroup, lock.lockGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, lockUntil, lockedAt, lockedBy, lockGroup);
    }
}
