package net.javacrumbs.shedlock.support;

import org.junit.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class LockRecordRegistryTest {
    private static final String NAME = "name";
    private final LockRecordRegistry lockRecordRegistry = new LockRecordRegistry();

    @Test
    public void unusedKeysShouldBeGarbageCollected() {
        int records = 1_000_000;
        for (int i = 0; i < records; i++) {
            lockRecordRegistry.addLockRecord(UUID.randomUUID().toString());
        }
        assertThat(lockRecordRegistry.getSize()).isLessThan(records);
    }

    @Test
    public void shouldRememberKeys() {
        lockRecordRegistry.addLockRecord(NAME);
        assertThat(lockRecordRegistry.lockRecordRecentlyCreated(NAME)).isTrue();
    }

    @Test
    public void shouldNotLie() {
        assertThat(lockRecordRegistry.lockRecordRecentlyCreated(NAME)).isFalse();
    }
}