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
package net.javacrumbs.shedlock.support;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LockRecordRegistryTest {
    private static final String NAME = "name";
    private final LockRecordRegistry lockRecordRegistry = new LockRecordRegistry();

    @Test
    void unusedKeysShouldBeGarbageCollected() {
        int records = 1_000_000;
        for (int i = 0; i < records; i++) {
            lockRecordRegistry.addLockRecord(UUID.randomUUID().toString());
        }
        assertThat(lockRecordRegistry.getSize()).isLessThan(records);
    }

    @Test
    void shouldRememberKeys() {
        lockRecordRegistry.addLockRecord(NAME);
        assertThat(lockRecordRegistry.lockRecordRecentlyCreated(NAME)).isTrue();
    }

    @Test
    void shouldNotLie() {
        assertThat(lockRecordRegistry.lockRecordRecentlyCreated(NAME)).isFalse();
    }

    @Test
    void shouldClear() {
        lockRecordRegistry.addLockRecord(NAME);
        assertThat(lockRecordRegistry.lockRecordRecentlyCreated(NAME)).isTrue();
        lockRecordRegistry.clear();
        assertThat(lockRecordRegistry.lockRecordRecentlyCreated(NAME)).isFalse();
    }
}
