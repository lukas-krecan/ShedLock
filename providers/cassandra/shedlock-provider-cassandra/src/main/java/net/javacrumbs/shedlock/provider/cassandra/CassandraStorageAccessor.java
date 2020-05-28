package net.javacrumbs.shedlock.provider.cassandra;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.Utils;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import java.time.Instant;
import java.util.Optional;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;

/**
 * StorageAccessor for cassandra.
 **/
/*
 * In theory, all the reads (find() method calls) in update methods are not necessary,
 * but it's a performance optimization. Moreover, the fuzzTest sometimes fails without them.
 */
class CassandraStorageAccessor extends AbstractStorageAccessor {
    private static final String LOCK_NAME = "name";
    private static final String LOCK_UNTIL = "lockUntil";
    private static final String LOCKED_AT = "lockedAt";
    private static final String LOCKED_BY = "lockedBy";

    private final String hostname;
    private final String table;
    private final CqlSession cqlSession;
    private final ConsistencyLevel consistencyLevel;

    CassandraStorageAccessor(@NonNull CqlSession cqlSession, @NonNull String table, @NonNull ConsistencyLevel consistencyLevel) {
        this.hostname = Utils.getHostname();
        this.table = table;
        this.cqlSession = cqlSession;
        this.consistencyLevel = consistencyLevel;
    }

    @Override
    public boolean insertRecord(@NonNull LockConfiguration lockConfiguration) {
        if (find(lockConfiguration.getName()).isPresent()) {
            return false;
        }

        return insert(lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());
    }

    @Override
    public boolean updateRecord(@NonNull LockConfiguration lockConfiguration) {
        Optional<Lock> lock = find(lockConfiguration.getName());
        if (!lock.isPresent() || lock.get().getLockUntil().isAfter(ClockProvider.now())) {
            return false;
        }

        return update(lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());
    }

    @Override
    public void unlock(@NonNull LockConfiguration lockConfiguration) {
        updateUntil(lockConfiguration.getName(), lockConfiguration.getUnlockTime());
    }

    @Override
    public boolean extend(@NonNull LockConfiguration lockConfiguration) {
        Optional<Lock> lock = find(lockConfiguration.getName());
        if (!lock.isPresent() || lock.get().getLockUntil().isBefore(ClockProvider.now()) || !lock.get().getLockedBy().equals(hostname)) {
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
            .column(LOCK_UNTIL)
            .column(LOCKED_AT)
            .column(LOCKED_BY)
            .whereColumn(LOCK_NAME).isEqualTo(literal(name))
            .build()
            .setConsistencyLevel(consistencyLevel);

        ResultSet resultSet = cqlSession.execute(selectStatement);
        Row row = resultSet.one();
        if (row != null) {
            return Optional.of(new Lock(row.getInstant(LOCK_UNTIL), row.getInstant(LOCKED_AT), row.getString(LOCKED_BY)));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Insert new lock row
     *
     * @param name  lock name
     * @param until new until instant value
     */
    private boolean insert(String name, Instant until) {
        return execute(QueryBuilder.insertInto(table)
                .value(LOCK_NAME, literal(name))
                .value(LOCK_UNTIL, literal(until))
                .value(LOCKED_AT, literal(ClockProvider.now()))
                .value(LOCKED_BY, literal(hostname))
                .ifNotExists()
                .build());
    }

    /**
     * Update existing lock row
     *
     * @param name  lock name
     * @param until new until instant value
     */
    private boolean update(String name, Instant until) {
        return execute(QueryBuilder.update(table)
                .setColumn(LOCK_UNTIL, literal(until))
                .setColumn(LOCKED_AT, literal(ClockProvider.now()))
                .setColumn(LOCKED_BY, literal(hostname))
                .whereColumn(LOCK_NAME).isEqualTo(literal(name))
                .ifColumn(LOCK_UNTIL).isLessThan(literal(ClockProvider.now()))
                .build());
    }

    /**
     * Updates lock.until field where lockConfiguration.name
     *
     * @param name  lock name
     * @param until new until instant value
     */
    private boolean updateUntil(String name, Instant until) {
        return execute(QueryBuilder.update(table)
                .setColumn(LOCK_UNTIL, literal(until))
                .whereColumn(LOCK_NAME).isEqualTo(literal(name))
                .ifColumn(LOCK_UNTIL).isGreaterThanOrEqualTo(literal(ClockProvider.now()))
                .ifColumn(LOCKED_BY).isEqualTo(literal(hostname))
                .build());
    }


    private boolean execute(SimpleStatement statement) {
        return cqlSession.execute(statement.setConsistencyLevel(consistencyLevel)).wasApplied();
    }
}
