package net.javacrumbs.shedlock.util;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;

public interface SimpleLockWithConfiguration extends SimpleLock {
    LockConfiguration getLockConfiguration();
}
