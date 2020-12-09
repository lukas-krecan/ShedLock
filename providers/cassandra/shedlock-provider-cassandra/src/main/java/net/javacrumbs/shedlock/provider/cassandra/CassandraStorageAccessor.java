package net.javacrumbs.shedlock.provider.cassandra;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;
import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.Optional;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;

import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.cassandra.CassandraLockProvider.Configuration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.Utils;
import net.javacrumbs.shedlock.support.annotation.NonNull;

/**
 * StorageAccessor for cassandra.
 **/
/*
 * In theory, all the reads (find() method calls) in update methods are not necessary,
 * but it's a performance optimization. Moreover, the fuzzTest sometimes fails without them.
 */
class CassandraStorageAccessor extends AbstractStorageAccessor {
    private final String hostname;
    private final String table;
    private final String lockName;
    private final String lockUntil;
    private final String lockedAt;
    private final String lockedBy;
    private final CqlSession cqlSession;
    private final ConsistencyLevel consistencyLevel;

    CassandraStorageAccessor(@NonNull Configuration configuration) {
    	requireNonNull(configuration, "configuration can not be null");
        this.hostname = Utils.getHostname();
        this.table = configuration.getTable();
        this.lockName = configuration.getColumnNames().getLockName();
        this.lockUntil = configuration.getColumnNames().getLockUntil();
        this.lockedAt = configuration.getColumnNames().getLockedAt();
        this.lockedBy = configuration.getColumnNames().getLockedBy();
        this.cqlSession = configuration.getCqlSession();
        this.consistencyLevel = configuration.getConsistencyLevel();
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
            .column(lockUntil)
            .column(lockedAt)
            .column(lockedBy)
            .whereColumn(lockName).isEqualTo(literal(name))
            .build()
            .setConsistencyLevel(consistencyLevel);

        ResultSet resultSet = cqlSession.execute(selectStatement);
        Row row = resultSet.one();
        if (row != null) {
            return Optional.of(new Lock(row.getInstant(lockUntil), row.getInstant(lockedAt), row.getString(lockedBy)));
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
                .value(lockName, literal(name))
                .value(lockUntil, literal(until))
                .value(lockedAt, literal(ClockProvider.now()))
                .value(lockedBy, literal(hostname))
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
                .setColumn(lockUntil, literal(until))
                .setColumn(lockedAt, literal(ClockProvider.now()))
                .setColumn(lockedBy, literal(hostname))
                .whereColumn(lockName).isEqualTo(literal(name))
                .ifColumn(lockUntil).isLessThan(literal(ClockProvider.now()))
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
                .setColumn(lockUntil, literal(until))
                .whereColumn(lockName).isEqualTo(literal(name))
                .ifColumn(lockUntil).isGreaterThanOrEqualTo(literal(ClockProvider.now()))
                .ifColumn(lockedBy).isEqualTo(literal(hostname))
                .build());
    }


    private boolean execute(SimpleStatement statement) {
        return cqlSession.execute(statement.setConsistencyLevel(consistencyLevel)).wasApplied();
    }
}