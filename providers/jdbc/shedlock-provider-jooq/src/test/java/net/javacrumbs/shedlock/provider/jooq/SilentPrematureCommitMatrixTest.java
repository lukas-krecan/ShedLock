package net.javacrumbs.shedlock.provider.jooq;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.time.Duration;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.test.support.jdbc.PostgresConfig;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Exhaustive matrix for the "silent premature commit" side effect: when JooqLockProvider's
 * DSLContext shares a connection with the caller's OWN unrelated, not-yet-committed work, a single
 * lock call force-commits that work - regardless of whether the lock is actually acquired, and
 * regardless of whether the caller later tries to roll back. Also verifies that using a genuinely
 * separate connection (the DataSource-based approach) avoids all of this entirely.
 */
class SilentPrematureCommitMatrixTest {
    private static final PostgresConfig dbConfig = new PostgresConfig();

    @BeforeAll
    static void startDb() {
        dbConfig.startDb();
    }

    @AfterAll
    static void shutdownDb() {
        dbConfig.shutdownDb();
    }

    private void resetTables() throws Exception {
        try (Connection setupConn = dbConfig.getDataSource().getConnection()) {
            setupConn
                    .createStatement()
                    .execute(
                            "CREATE TABLE IF NOT EXISTS shedlock(name VARCHAR(64) NOT NULL PRIMARY KEY, "
                                    + "lock_until TIMESTAMP NOT NULL, locked_at TIMESTAMP NOT NULL, locked_by VARCHAR(255) NOT NULL)");
            setupConn.createStatement().execute("CREATE TABLE IF NOT EXISTS marker(id INT PRIMARY KEY)");
            setupConn.createStatement().execute("DELETE FROM shedlock");
            setupConn.createStatement().execute("DELETE FROM marker");
        }
    }

    private int markerCountFromFreshConnection() throws Exception {
        try (Connection freshConn = dbConfig.getDataSource().getConnection();
                var stmt = freshConn.createStatement();
                var rs = stmt.executeQuery("SELECT COUNT(*) FROM marker")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // 1. Baseline (already proven, re-verified here): one successful lock call commits caller's work.
    @Test
    void successfulLockCall_commitsCallersOtherPendingWork() throws Exception {
        resetTables();
        try (Connection ambientConn = dbConfig.getDataSource().getConnection()) {
            ambientConn.setAutoCommit(false);
            try (var stmt = ambientConn.createStatement()) {
                stmt.execute("INSERT INTO marker(id) VALUES (1)");
            }
            assertThat(markerCountFromFreshConnection()).isZero();

            JooqStorageAccessor accessor = new JooqStorageAccessor(DSL.using(ambientConn, SQLDialect.POSTGRES));
            boolean acquired = accessor.insertRecord(
                    new LockConfiguration(java.time.Instant.now(), "lock-1", Duration.ofMinutes(5), Duration.ZERO));

            assertThat(acquired).isTrue();
            System.out.println("[1] marker count after SUCCESSFUL lock call: " + markerCountFromFreshConnection());
            assertThat(markerCountFromFreshConnection()).isEqualTo(1);
        }
    }

    // 2. Does a FAILED lock attempt (someone else already holds it) ALSO force-commit caller's work?
    @Test
    void failedLockAttempt_stillCommitsCallersOtherPendingWork() throws Exception {
        resetTables();
        // Someone else already holds "lock-2" (a completely independent, already-committed lock).
        try (Connection winnerConn = dbConfig.getDataSource().getConnection()) {
            JooqStorageAccessor winner = new JooqStorageAccessor(DSL.using(winnerConn, SQLDialect.POSTGRES));
            winner.insertRecord(
                    new LockConfiguration(java.time.Instant.now(), "lock-2", Duration.ofMinutes(5), Duration.ZERO));
        }

        try (Connection ambientConn = dbConfig.getDataSource().getConnection()) {
            ambientConn.setAutoCommit(false);
            try (var stmt = ambientConn.createStatement()) {
                stmt.execute("INSERT INTO marker(id) VALUES (1)");
            }
            assertThat(markerCountFromFreshConnection()).isZero();

            JooqStorageAccessor accessor = new JooqStorageAccessor(DSL.using(ambientConn, SQLDialect.POSTGRES));
            // This attempt LOSES the race - lock-2 is already held.
            boolean acquired = accessor.insertRecord(
                    new LockConfiguration(java.time.Instant.now(), "lock-2", Duration.ofMinutes(5), Duration.ZERO));

            assertThat(acquired).isFalse();
            System.out.println("[2] marker count after FAILED (losing) lock call: " + markerCountFromFreshConnection());
            // Even a losing attempt still runs inside (and commits) the top-level transaction.
            assertThat(markerCountFromFreshConnection()).isEqualTo(1);
        }
    }

    // 3. If the caller INTENDED to roll back (e.g. a downstream validation failure), does the lock
    // call's forced commit make that rollback silently ineffective for the already-committed part?
    @Test
    void callerIntendedRollback_isDefeated_byLockCallsForcedCommit() throws Exception {
        resetTables();
        try (Connection ambientConn = dbConfig.getDataSource().getConnection()) {
            ambientConn.setAutoCommit(false);
            try (var stmt = ambientConn.createStatement()) {
                stmt.execute("INSERT INTO marker(id) VALUES (1)");
            }

            JooqStorageAccessor accessor = new JooqStorageAccessor(DSL.using(ambientConn, SQLDialect.POSTGRES));
            accessor.insertRecord(
                    new LockConfiguration(java.time.Instant.now(), "lock-3", Duration.ofMinutes(5), Duration.ZERO));

            // Simulate the caller later deciding something went wrong and rolling back.
            ambientConn.rollback();

            System.out.println("[3] marker count after caller's explicit rollback (post lock call): "
                    + markerCountFromFreshConnection());
            // The rollback is too late - marker(1) was already committed by the lock call.
            assertThat(markerCountFromFreshConnection()).isEqualTo(1);
        }
    }

    // 4. Does MULTIPLE prior pending writes (not just one row) all get swept into the forced commit?
    @Test
    void multiplePendingWrites_areAllCommitted_byOneLockCall() throws Exception {
        resetTables();
        try (Connection ambientConn = dbConfig.getDataSource().getConnection()) {
            ambientConn.setAutoCommit(false);
            try (var stmt = ambientConn.createStatement()) {
                stmt.execute("INSERT INTO marker(id) VALUES (1)");
                stmt.execute("INSERT INTO marker(id) VALUES (2)");
                stmt.execute("INSERT INTO marker(id) VALUES (3)");
            }
            assertThat(markerCountFromFreshConnection()).isZero();

            JooqStorageAccessor accessor = new JooqStorageAccessor(DSL.using(ambientConn, SQLDialect.POSTGRES));
            accessor.insertRecord(
                    new LockConfiguration(java.time.Instant.now(), "lock-4", Duration.ofMinutes(5), Duration.ZERO));

            System.out.println("[4] marker count after lock call (3 unrelated rows pending before it): "
                    + markerCountFromFreshConnection());
            assertThat(markerCountFromFreshConnection()).isEqualTo(3);
        }
    }

    // 5. unlock() and extend() also force-commit, not just insertRecord().
    @Test
    void unlockAndExtend_alsoForceCommit_callersOtherPendingWork() throws Exception {
        resetTables();
        try (Connection ambientConn = dbConfig.getDataSource().getConnection()) {
            ambientConn.setAutoCommit(false);
            JooqStorageAccessor accessor = new JooqStorageAccessor(DSL.using(ambientConn, SQLDialect.POSTGRES));
            LockConfiguration lockConfiguration =
                    new LockConfiguration(java.time.Instant.now(), "lock-5", Duration.ofMinutes(1), Duration.ZERO);
            accessor.insertRecord(lockConfiguration); // this alone already commits, so add new pending work after

            try (var stmt = ambientConn.createStatement()) {
                stmt.execute("INSERT INTO marker(id) VALUES (1)");
            }
            assertThat(markerCountFromFreshConnection()).isZero();

            accessor.extend(
                    new LockConfiguration(java.time.Instant.now(), "lock-5", Duration.ofMinutes(5), Duration.ZERO));
            System.out.println("[5a] marker count after extend(): " + markerCountFromFreshConnection());
            assertThat(markerCountFromFreshConnection()).isEqualTo(1);

            try (var stmt = ambientConn.createStatement()) {
                stmt.execute("INSERT INTO marker(id) VALUES (2)");
            }
            assertThat(markerCountFromFreshConnection()).isEqualTo(1); // id=2 not committed yet

            accessor.unlock(lockConfiguration);
            System.out.println("[5b] marker count after unlock(): " + markerCountFromFreshConnection());
            assertThat(markerCountFromFreshConnection()).isEqualTo(2);
        }
    }

    // 6. Control: a genuinely SEPARATE connection (the DataSource-based idea) never touches the
    // caller's own connection/transaction at all, so none of the above can happen.
    @Test
    void separateConnection_neverCommitsCallersOtherPendingWork() throws Exception {
        resetTables();
        try (Connection ambientConn = dbConfig.getDataSource().getConnection()) {
            ambientConn.setAutoCommit(false);
            try (var stmt = ambientConn.createStatement()) {
                stmt.execute("INSERT INTO marker(id) VALUES (1)");
            }
            assertThat(markerCountFromFreshConnection()).isZero();

            // The lock call runs on a COMPLETELY SEPARATE connection obtained fresh from the
            // DataSource - never touches ambientConn at all.
            try (Connection lockConn = dbConfig.getDataSource().getConnection()) {
                JooqStorageAccessor accessor = new JooqStorageAccessor(DSL.using(lockConn, SQLDialect.POSTGRES));
                boolean acquired = accessor.insertRecord(
                        new LockConfiguration(java.time.Instant.now(), "lock-6", Duration.ofMinutes(5), Duration.ZERO));
                assertThat(acquired).isTrue();
            }

            System.out.println(
                    "[6] marker count after lock call on a SEPARATE connection: " + markerCountFromFreshConnection());
            // Caller's own pending work is completely untouched.
            assertThat(markerCountFromFreshConnection()).isZero();

            // And the caller can still decide to roll back cleanly afterward.
            ambientConn.rollback();
            assertThat(markerCountFromFreshConnection()).isZero();
        }
    }
}
