package net.javacrumbs.shedlock.provider.jooq;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.time.Duration;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.test.support.jdbc.PostgresConfig;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Follow-up: the stale-now() issue is not just a cosmetic timestamp discrepancy - it can break
 * the actual mutual-exclusion guarantee. Node A has a long-lived ambient transaction open before
 * it acquires the lock, so its lock_until is pinned to a stale, earlier now(). Node B connects
 * fresh with no ambient transaction. With a short enough lockAtMostFor, Node B can steal the lock
 * while Node A still believes it is well within lockAtMostFor.
 */
class AmbientTransactionDoubleAcquireReproTest {
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
    void two_nodes_can_hold_the_same_lock_at_the_same_time() throws Exception {
        try (Connection setupConn = dbConfig.getDataSource().getConnection()) {
            setupConn
                    .createStatement()
                    .execute(
                            "CREATE TABLE IF NOT EXISTS shedlock(name VARCHAR(64) NOT NULL PRIMARY KEY, "
                                    + "lock_until TIMESTAMP NOT NULL, locked_at TIMESTAMP NOT NULL, locked_by VARCHAR(255) NOT NULL)");
            setupConn.createStatement().execute("DELETE FROM shedlock");
        }

        Duration lockAtMostFor = Duration.ofSeconds(2);
        LockConfiguration lockConfiguration =
                new LockConfiguration(java.time.Instant.now(), "double-acquire-lock", lockAtMostFor, Duration.ZERO);

        try (Connection nodeAConn = dbConfig.getDataSource().getConnection()) {
            nodeAConn.setAutoCommit(false);
            try (var ps = nodeAConn.prepareStatement("SELECT 1")) {
                ps.executeQuery();
            }
            Thread.sleep(3000);

            JooqStorageAccessor accessorA = new JooqStorageAccessor(DSL.using(nodeAConn, SQLDialect.POSTGRES));
            boolean nodeAAcquired = accessorA.insertRecord(lockConfiguration);
            assertThat(nodeAAcquired).isTrue();

            try (Connection nodeBConn = dbConfig.getDataSource().getConnection()) {
                DSLContext dslNodeB = DSL.using(nodeBConn, SQLDialect.POSTGRES);
                JooqStorageAccessor accessorB = new JooqStorageAccessor(dslNodeB);

                boolean nodeBAcquiredViaInsert = accessorB.insertRecord(lockConfiguration);
                boolean nodeBAcquiredViaUpdate = !nodeBAcquiredViaInsert && accessorB.updateRecord(lockConfiguration);

                assertThat(nodeBAcquiredViaInsert || nodeBAcquiredViaUpdate)
                        .as("Node B should NOT be able to steal a lock Node A just acquired with a 2s "
                                + "lockAtMostFor, unless jOOQ's stale-now() bug lets it")
                        .isTrue();
            }
        }
    }
}
