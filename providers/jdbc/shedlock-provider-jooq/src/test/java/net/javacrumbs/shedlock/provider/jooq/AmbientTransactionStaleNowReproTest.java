package net.javacrumbs.shedlock.provider.jooq;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDateTime;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.test.support.jdbc.PostgresConfig;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Reproduction: when the DSLContext given to JooqLockProvider (default, no DataSource) is bound
 * directly to a raw Connection that already has an open transaction, lock_until is computed from
 * that transaction's stale snapshot instead of the actual current time. FuzzTester never exercises
 * this, since it calls the lock provider directly with no ambient transaction.
 */
class AmbientTransactionStaleNowReproTest {
    private static final PostgresConfig dbConfig = new PostgresConfig();

    @BeforeAll
    static void startDb() {
        dbConfig.startDb();
    }

    @AfterAll
    static void shutdownDb() {
        dbConfig.shutdownDb();
    }

    @Test
    void lock_until_is_computed_from_stale_transaction_snapshot_not_actual_now() throws Exception {
        try (Connection setupConn = dbConfig.getDataSource().getConnection()) {
            setupConn
                    .createStatement()
                    .execute(
                            "CREATE TABLE IF NOT EXISTS shedlock(name VARCHAR(64) NOT NULL PRIMARY KEY, "
                                    + "lock_until TIMESTAMP NOT NULL, locked_at TIMESTAMP NOT NULL, locked_by VARCHAR(255) NOT NULL)");
            setupConn.createStatement().execute("DELETE FROM shedlock");
        }

        try (Connection ambientConn = dbConfig.getDataSource().getConnection()) {
            ambientConn.setAutoCommit(false);

            LocalDateTime txSnapshotAtStart;
            try (PreparedStatement ps = ambientConn.prepareStatement("SELECT CURRENT_TIMESTAMP")) {
                ResultSet rs = ps.executeQuery();
                rs.next();
                txSnapshotAtStart = rs.getTimestamp(1).toLocalDateTime();
            }
            Thread.sleep(3000);

            DSLContext dslOnAmbientConnection = DSL.using(ambientConn, SQLDialect.POSTGRES);
            JooqStorageAccessor accessor = new JooqStorageAccessor(dslOnAmbientConnection);

            boolean inserted = accessor.insertRecord(
                    new LockConfiguration(java.time.Instant.now(), "repro-lock", Duration.ofMinutes(5), Duration.ZERO));
            assertThat(inserted).isTrue();

            LocalDateTime lockUntilRecorded;
            try (PreparedStatement ps =
                    ambientConn.prepareStatement("SELECT lock_until FROM shedlock WHERE name = 'repro-lock'")) {
                ResultSet rs = ps.executeQuery();
                rs.next();
                lockUntilRecorded = rs.getTimestamp(1).toLocalDateTime();
            }

            assertThat(Duration.between(txSnapshotAtStart, lockUntilRecorded))
                    .as("lock_until should equal txSnapshotAtStart + 5min if the stale snapshot was used")
                    .isCloseTo(Duration.ofMinutes(5), Duration.ofSeconds(1));
        }
    }
}
