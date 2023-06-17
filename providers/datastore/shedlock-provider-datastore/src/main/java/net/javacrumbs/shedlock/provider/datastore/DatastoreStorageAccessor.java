package net.javacrumbs.shedlock.provider.datastore;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreException;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.Transaction;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

public class DatastoreStorageAccessor extends AbstractStorageAccessor {
    private static final Logger log = LoggerFactory.getLogger(DatastoreStorageAccessor.class);

    private final Datastore datastore;
    private final String hostname;
    private final String entityName;
    private final DatastoreLockProvider.Fields fields;

    public DatastoreStorageAccessor(DatastoreLockProvider.Configuration configuration) {
        requireNonNull(configuration);
        this.datastore = configuration.getDatastore();
        this.hostname = Utils.getHostname();
        this.entityName = configuration.getEntityName();
        this.fields = configuration.getFields();
    }

    @Override
    public boolean insertRecord(LockConfiguration config) {
        return insert(config.getName(), config.getLockAtMostUntil());
    }

    @Override
    public boolean updateRecord(LockConfiguration config) {
        return updateExisting(config.getName(), config.getLockAtMostUntil());
    }

    @Override
    public void unlock(LockConfiguration config) {
        updateOwn(config.getName(), config.getUnlockTime());
    }

    @Override
    public boolean extend(LockConfiguration config) {
        return updateOwn(config.getName(), config.getLockAtMostUntil());
    }

    private boolean insert(String name, Instant until) {
        return doInTxn(txn -> {
            KeyFactory keyFactory = this.datastore.newKeyFactory().setKind(this.entityName);
            Key key = keyFactory.newKey(name);
            Entity entity = Entity.newBuilder(key)
                .set(this.fields.lockUntil(), fromInstant(until))
                .set(this.fields.lockedAt(), fromInstant(ClockProvider.now()))
                .set(this.fields.lockedBy(), this.hostname)
                .build();
            txn.add(entity);
            txn.commit();
            return Optional.of(true);
        }).orElse(false);
    }

    private boolean updateExisting(String name, Instant until) {
        return doInTxn(txn ->
            get(name, txn)
                .filter(entity -> {
                    var now = ClockProvider.now();
                    var lockUntilTs = nullableTimestamp(entity, this.fields.lockUntil());
                    return lockUntilTs != null && lockUntilTs.isBefore(now);
                })
                .map(entity -> {
                    txn.put(Entity.newBuilder(entity)
                        .set(this.fields.lockUntil(), fromInstant(until))
                        .set(this.fields.lockedAt(), fromInstant(ClockProvider.now()))
                        .set(this.fields.lockedBy(), this.hostname)
                        .build());
                    txn.commit();
                    return true;
                })
        ).orElse(false);
    }

    private boolean updateOwn(String name, Instant until) {
        return doInTxn(txn ->
            get(name, txn)
                .filter(entity -> this.hostname.equals(nullableString(entity, this.fields.lockedBy())))
                .filter(entity -> {
                    var now = ClockProvider.now();
                    var lockUntilTs = nullableTimestamp(entity, this.fields.lockUntil());
                    return lockUntilTs != null && (lockUntilTs.isAfter(now) || lockUntilTs.equals(now));
                })
                .map(entity -> {
                    txn.put(Entity.newBuilder(entity)
                        .set(this.fields.lockUntil(), fromInstant(until))
                        .build());
                    txn.commit();
                    return true;
                })
        ).orElse(false);
    }

    public Optional<Lock> findLock(String name) {
        return get(name)
            .map(entity -> new Lock(
                entity.getKey().getName(),
                nullableTimestamp(entity, this.fields.lockedAt()),
                nullableTimestamp(entity, this.fields.lockUntil()),
                nullableString(entity, this.fields.lockedBy())
            ));
    }

    private Optional<Entity> get(String name) {
        KeyFactory keyFactory = this.datastore.newKeyFactory().setKind(this.entityName);
        Key key = keyFactory.newKey(name);
        return ofNullable(this.datastore.get(key));
    }

    private Optional<Entity> get(String name, Transaction txn) {
        KeyFactory keyFactory = this.datastore.newKeyFactory().setKind(this.entityName);
        Key key = keyFactory.newKey(name);
        return ofNullable(txn.get(key));
    }

    private <T> Optional<T> doInTxn(Function<Transaction, Optional<T>> work) {
        var txn = this.datastore.newTransaction();
        try {
            return work.apply(txn);
        } catch (DatastoreException ex) {
            log.debug("Unable to perform a transactional unit of work: {}", ex.getMessage());
            return Optional.empty();
        } finally {
            if (txn.isActive()) {
                txn.rollback();
            }
        }
    }

    private static String nullableString(Entity entity, String property) {
        return entity.contains(property) ? entity.getString(property) : null;
    }

    private static Instant nullableTimestamp(Entity entity, String property) {
        return entity.contains(property) ? toInstant(entity.getTimestamp(property)) : null;
    }

    private static Timestamp fromInstant(Instant instant) {
        return Timestamp.of(java.sql.Timestamp.from(requireNonNull(instant)));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return requireNonNull(timestamp).toSqlTimestamp().toInstant();
    }

    public record Lock(String name, Instant lockedAt, Instant lockedUntil, String lockedBy) {
    }
}
