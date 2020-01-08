package net.javacrumbs.shedlock.provider.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.Utils;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Optional;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;

public class CassandraStorageAccessor extends AbstractStorageAccessor {

    private static final String LOCK_NAME = "name";
    private static final String LOCK_UNTIL = "lockUntil";
    private static final String LOCKED_AT = "lockedAt";
    private static final String LOCKED_BY = "lockedBy";

    private final String hostname;
    private final String table;
    private final CqlSession cqlSession;

    public CassandraStorageAccessor(@NotNull String contactPoint, @NotNull int port, @NotNull String datacenter, @NotNull String keyspace, @NotNull String table) {
        this.hostname = Utils.getHostname();
        this.table = table;
        cqlSession = CqlSession.builder()
                .addContactPoint(new InetSocketAddress(contactPoint, port))
                .withLocalDatacenter(datacenter)
                .withKeyspace(keyspace)
                .build();
    }

    public CassandraStorageAccessor(@NotNull CqlSession cqlSession, @NotNull String table) {
        this.hostname = Utils.getHostname();
        this.table = table;
        this.cqlSession = cqlSession;
    }

    @Override
    public boolean insertRecord(@NotNull LockConfiguration lockConfiguration) {
        if (find(lockConfiguration.getName()).isPresent()) {
            return false;
        }

        return insert(lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());
    }

    @Override
    public boolean updateRecord(@NotNull LockConfiguration lockConfiguration) {
        Optional<Lock> lock = find(lockConfiguration.getName());
        if (!lock.isPresent() || lock.get().getLockUntil().isAfter(Instant.now())) {
            return false;
        }

        return update(lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());
    }

    @Override
    public void unlock(@NotNull LockConfiguration lockConfiguration) {
        updateUntil(lockConfiguration.getName(), lockConfiguration.getUnlockTime());
    }

    @Override
    public boolean extend(@NotNull LockConfiguration lockConfiguration) {
        Optional<Lock> lock = find(lockConfiguration.getName());
        if (!lock.isPresent() || lock.get().getLockUntil().isBefore(Instant.now()) || !lock.get().getLockedBy().equals(hostname)) {
            logger.trace("extend false");
            return false;
        }

        return updateUntil(lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());
    }

    /**
     * Find existing row by primary key lock.name
     *
     * @param name lock name
     * @return optional lock row or empty
     */
    Optional<Lock> find(String name) {
        SimpleStatement selectStatement = QueryBuilder.selectFrom(table)
                .column(LOCK_NAME)
                .column(LOCK_UNTIL)
                .column(LOCKED_AT)
                .column(LOCKED_BY)
                .whereColumn(LOCK_NAME).isEqualTo(literal(name))
                .build();

        ResultSet resultSet = cqlSession.execute(selectStatement);
        Row row = resultSet.one();
        return row != null ?
                Optional.of(new Lock(row.getString(LOCK_NAME), row.getInstant(LOCK_UNTIL), row.getInstant(LOCKED_AT), row.getString(LOCKED_BY))) :
                Optional.empty();
    }

    /**
     * Insert new lock row
     *
     * @param name  lock name
     * @param until new until instant value
     */
    boolean insert(String name, Instant until) {
        SimpleStatement insertStatement = QueryBuilder.insertInto(table)
                .value(LOCK_NAME, literal(name))
                .value(LOCK_UNTIL, literal(until))
                .value(LOCKED_AT, literal(Instant.now()))
                .value(LOCKED_BY, literal(hostname))
                .ifNotExists()
                .build();

        ResultSet resultSet = cqlSession.execute(insertStatement);
        if (resultSet == null) {
            return false;
        }
        return resultSet.wasApplied();
    }

    /**
     * Update existing lock row
     *
     * @param name  lock name
     * @param until new until instant value
     */
    boolean update(String name, Instant until) {
        SimpleStatement updateStatement = QueryBuilder.update(table)
                .setColumn(LOCK_UNTIL, literal(until))
                .setColumn(LOCKED_AT, literal(Instant.now()))
                .setColumn(LOCKED_BY, literal(hostname))
                .whereColumn(LOCK_NAME).isEqualTo(literal(name))
                .ifColumn(LOCK_UNTIL).isLessThan(literal(Instant.now()))
                .build();

        ResultSet resultSet = cqlSession.execute(updateStatement);
        if (resultSet == null) {
            return false;
        }
        return resultSet.wasApplied();
    }

    /**
     * Updates lock.until field where lockConfiguration.name
     *
     * @param name  lock name
     * @param until new until instant value
     */
    boolean updateUntil(String name, Instant until) {
        SimpleStatement updateStatement = QueryBuilder.update(table)
                .setColumn(LOCK_UNTIL, literal(until))
                .whereColumn(LOCK_NAME).isEqualTo(literal(name))
                .ifColumn(LOCK_UNTIL).isGreaterThanOrEqualTo(literal(Instant.now()))
                .ifColumn(LOCKED_BY).isEqualTo(literal(hostname))
                .build();

        ResultSet resultSet = cqlSession.execute(updateStatement);
        if (resultSet == null) {
            return false;
        }
        return resultSet.wasApplied();
    }
}
