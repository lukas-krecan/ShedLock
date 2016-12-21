package net.javacrumbs.shedlock.core.support;

import org.junit.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class LockRecordRegistryTest {
    private final LockRecordRegistry lockRecordRegistry = new LockRecordRegistry();

    @Test
    public void unusedKeysShouldBeGarbageCollected() {
        int records = 1_000_000;
        for (int i = 0; i < records; i++) {
            lockRecordRegistry.addLockRecord(UUID.randomUUID().toString());
        }
        assertThat(lockRecordRegistry.getSize()).isLessThan(records);
    }

}