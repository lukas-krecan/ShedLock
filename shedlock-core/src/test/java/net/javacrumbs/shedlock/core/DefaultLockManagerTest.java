package net.javacrumbs.shedlock.core;


import org.junit.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class DefaultLockManagerTest {

    public static final LockConfiguration LOCK_CONFIGURATION = new LockConfiguration("name", Instant.now());
    private final LockProvider lockProvider = mock(LockProvider.class);
    private final LockConfigurationExtractor lockConfigurationExtractor = mock(LockConfigurationExtractor.class);
    private final Runnable task = mock(Runnable.class);
    private final SimpleLock lock = mock(SimpleLock.class);

    private final DefaultLockManager defaultLockManager = new DefaultLockManager(lockProvider, lockConfigurationExtractor);


    @Test
    public void noConfigNoLock() {
        when(lockConfigurationExtractor.getLockConfiguration(task)).thenReturn(Optional.empty());

        defaultLockManager.executeIfNotLocked(task);
        verify(task).run();
        verifyZeroInteractions(lockProvider);
    }

    @Test
    public void executeIfLockAvailable() {
        when(lockConfigurationExtractor.getLockConfiguration(task)).thenReturn(Optional.of(LOCK_CONFIGURATION));
        when(lockProvider.lock(LOCK_CONFIGURATION)).thenReturn(Optional.of(lock));

        defaultLockManager.executeIfNotLocked(task);
        verify(task).run();
        InOrder inOrder = inOrder(task, lock);
        inOrder.verify(task).run();
        inOrder.verify(lock).unlock();
    }

    @Test
    public void doNotExecuteIfAlreadyLocked() {
        when(lockConfigurationExtractor.getLockConfiguration(task)).thenReturn(Optional.of(LOCK_CONFIGURATION));
        when(lockProvider.lock(LOCK_CONFIGURATION)).thenReturn(Optional.empty());

        defaultLockManager.executeIfNotLocked(task);
        verifyZeroInteractions(task);
    }

}