package net.javacrumbs.shedlock.provider.hazelcast;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

import java.util.Optional;


public class HazelcastLockProvider implements LockProvider {

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        return null;
    }

}
