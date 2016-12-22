package net.javacrumbs.shedlock.provider.mongo;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider.MongoAccessor;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MongoLockProviderTest {
    public static final LockConfiguration LOCK_CONFIGURATION = new LockConfiguration("name", Instant.now().plus(5, ChronoUnit.MINUTES));

    private MongoAccessor mongoAccessor = mock(MongoAccessor.class);

    private final MongoLockProvider lockProvider = new MongoLockProvider(mongoAccessor);

    @Test
    public void newRecordShouldOnlyBeInserted() {
        when(mongoAccessor.insertRecord(LOCK_CONFIGURATION)).thenReturn(true);
        assertThat(lockProvider.lock(LOCK_CONFIGURATION)).isNotEmpty();
        verify(mongoAccessor, never()).updateRecord(LOCK_CONFIGURATION);

        // Should update directly without insert
        reset(mongoAccessor);
        when(mongoAccessor.updateRecord(LOCK_CONFIGURATION)).thenReturn(true);
        assertThat(lockProvider.lock(LOCK_CONFIGURATION)).isNotEmpty();
        verify(mongoAccessor, never()).insertRecord(LOCK_CONFIGURATION);
        verify(mongoAccessor).updateRecord(LOCK_CONFIGURATION);
    }

    @Test
    public void updateOnDuplicateKey() {
        when(mongoAccessor.insertRecord(LOCK_CONFIGURATION)).thenReturn(false);
        when(mongoAccessor.updateRecord(LOCK_CONFIGURATION)).thenReturn(true);
        assertThat(lockProvider.lock(LOCK_CONFIGURATION)).isNotEmpty();
        verify(mongoAccessor).updateRecord(LOCK_CONFIGURATION);

        // Should update directly without insert
        reset(mongoAccessor);
        when(mongoAccessor.updateRecord(LOCK_CONFIGURATION)).thenReturn(true);
        assertThat(lockProvider.lock(LOCK_CONFIGURATION)).isNotEmpty();
        verify(mongoAccessor, never()).insertRecord(LOCK_CONFIGURATION);
        verify(mongoAccessor).updateRecord(LOCK_CONFIGURATION);
    }

    @Test
    public void doNotReturnLockIfUpdatedZeroRows() {
        when(mongoAccessor.insertRecord(LOCK_CONFIGURATION)).thenReturn(false);
        when(mongoAccessor.updateRecord(LOCK_CONFIGURATION)).thenReturn(false);
        assertThat(lockProvider.lock(LOCK_CONFIGURATION)).isEmpty();
    }
}