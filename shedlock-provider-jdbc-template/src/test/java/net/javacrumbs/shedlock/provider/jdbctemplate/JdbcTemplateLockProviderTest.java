package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.core.LockConfiguration;
import org.junit.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JdbcTemplateLockProviderTest {
    public static final LockConfiguration LOCK_CONFIGURATION = new LockConfiguration("name", Instant.now().plus(5, ChronoUnit.MINUTES));
    private final NamedParameterJdbcOperations jdbcTemplate = mock(NamedParameterJdbcOperations.class);
    private final JdbcTemplateLockProvider lockProvider = new JdbcTemplateLockProvider(jdbcTemplate, "ShedLock");

    @Test
    public void newRecordShouldOnlyBeInserted() {
        when(jdbcTemplate.update(insertStatement(), anyParams())).thenReturn(1);
        assertThat(lockProvider.lock(LOCK_CONFIGURATION)).isNotEmpty();
        verify(jdbcTemplate, never()).update(updateStatement(), anyParams());

        // Should update directly without insert
        reset(jdbcTemplate);
        when(jdbcTemplate.update(updateStatement(), anyParams())).thenReturn(1);
        assertThat(lockProvider.lock(LOCK_CONFIGURATION)).isNotEmpty();
        verify(jdbcTemplate, never()).update(insertStatement(), anyParams());
        verify(jdbcTemplate).update(updateStatement(), anyParams());
    }

    @Test
    public void updateOnDuplicateKey() {
        when(jdbcTemplate.update(insertStatement(), anyParams())).thenThrow(new DuplicateKeyException("Duplicate key"));
        when(jdbcTemplate.update(updateStatement(), anyParams())).thenReturn(1);
        assertThat(lockProvider.lock(LOCK_CONFIGURATION)).isNotEmpty();
        verify(jdbcTemplate).update(updateStatement(), anyParams());

        // Should update directly without insert
        reset(jdbcTemplate);
        when(jdbcTemplate.update(updateStatement(), anyParams())).thenReturn(1);
        assertThat(lockProvider.lock(LOCK_CONFIGURATION)).isNotEmpty();
        verify(jdbcTemplate, never()).update(insertStatement(), anyParams());
        verify(jdbcTemplate).update(updateStatement(), anyParams());
    }

    @Test
    public void doNotReturnLockIfUpdatedZeroRows() {
        when(jdbcTemplate.update(insertStatement(), anyParams())).thenThrow(new DuplicateKeyException("Duplicate key"));
        when(jdbcTemplate.update(updateStatement(), anyParams())).thenReturn(0);
        assertThat(lockProvider.lock(LOCK_CONFIGURATION)).isEmpty();
    }

    protected String updateStatement() {
        return startsWith("UPDATE");
    }

    protected String insertStatement() {
        return startsWith("INSERT");
    }

    protected Map<String, Object> anyParams() {
        return anyMapOf(String.class, Object.class);
    }

}