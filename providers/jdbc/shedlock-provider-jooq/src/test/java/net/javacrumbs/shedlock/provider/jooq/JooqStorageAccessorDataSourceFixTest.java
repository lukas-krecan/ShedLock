package net.javacrumbs.shedlock.provider.jooq;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
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
 * Verifies the JooqLockProvider(DSLContext, DataSource) constructor fixes both problems that a
 * raw-Connection-bound DSLContext otherwise has: (a) lock_until is computed from the real current
 * time rather than a stale ambient-transaction snapshot, and (b) the caller's own unrelated
 * pending work on that connection is never touched or force-committed, and still rolls back
 * cleanly afterward.
 */
class JooqStorageAccessorDataSourceFixTest {
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
    void usesRealCurrentTime_andNeverTouchesCallersOwnConnection() throws Exception {
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

        try (Connection callerConn = dbConfig.getDataSource().getConnection()) {
            callerConn.setAutoCommit(false);
            try (var stmt = callerConn.createStatement()) {
                stmt.execute("INSERT INTO marker(id) VALUES (1)");
            }
            Thread.sleep(3000);

            DSLContext callerDsl = DSL.using(callerConn, SQLDialect.POSTGRES);
            JooqStorageAccessor accessor = new JooqStorageAccessor(callerDsl, dbConfig.getDataSource());

            LocalDateTime realNow = LocalDateTime.now();
            boolean acquired = accessor.insertRecord(
                    new LockConfiguration(java.time.Instant.now(), "fix-lock", Duration.ofMinutes(5), Duration.ZERO));
            assertThat(acquired).isTrue();

            // (a) lock_until reflects the real current time, not a stale ambient snapshot.
            LocalDateTime lockUntil;
            try (var stmt = callerConn.createStatement();
                    var rs = stmt.executeQuery("SELECT lock_until FROM shedlock WHERE name = 'fix-lock'")) {
                rs.next();
                lockUntil = rs.getTimestamp(1).toLocalDateTime();
            }
            assertThat(Duration.between(realNow, lockUntil)).isCloseTo(Duration.ofMinutes(5), Duration.ofSeconds(2));

            // (b) the caller's own unrelated pending work is untouched and still rolls back cleanly.
            callerConn.rollback();
        }

        try (Connection verifyConn = dbConfig.getDataSource().getConnection();
                var stmt = verifyConn.createStatement();
                var rs = stmt.executeQuery("SELECT COUNT(*) FROM marker")) {
            rs.next();
            assertThat(rs.getInt(1))
                    .as("caller's own uncommitted work must not have been force-committed by the lock call")
                    .isZero();
        }
    }
}
