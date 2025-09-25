package net.javacrumbs.shedlock.provider.vertx;

import static net.javacrumbs.shedlock.provider.vertx.NamedSql.translate;

import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.sql.SqlConfiguration;
import net.javacrumbs.shedlock.provider.sql.SqlStatementsSource;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.LockException;

class VertxSqlClientStorageAccessor extends AbstractStorageAccessor {
    private final VertxSqlClientLockProvider.Configuration configuration;
    private final Pool pool;

    private volatile SqlStatementsSource sqlStatementsSource;

    VertxSqlClientStorageAccessor(VertxSqlClientLockProvider.Configuration configuration) {
        this.configuration = configuration;
        this.pool = configuration.getPool();
    }

    @Override
    public boolean insertRecord(LockConfiguration lockConfiguration) {
        NamedSql.Statement stmt = translate(sqlStatementsSource().getInsertStatement(), sqlStatementsSource().params(lockConfiguration));
        try {
            int updated = executeUpdate(stmt.sql(), stmt.parameters());
            return updated > 0;
        } catch (Exception e) {
            // same semantics as JDBC implementation - ignore errors on insert and return false
            logger.debug("Exception thrown when inserting record", e);
            return false;
        }
    }

    @Override
    public boolean updateRecord(LockConfiguration lockConfiguration) {
        NamedSql.Statement stmt = translate(sqlStatementsSource().getUpdateStatement(), sqlStatementsSource().params(lockConfiguration));
        try {
            int updated = executeUpdate(stmt.sql(), stmt.parameters());
            return updated > 0;
        } catch (Exception e) {
            logger.debug("Unexpected exception when updating lock record", e);
            throw new LockException("Unexpected exception when locking", unwrap(e));
        }
    }

    @Override
    public boolean extend(LockConfiguration lockConfiguration) {
        NamedSql.Statement stmt = translate(sqlStatementsSource().getExtendStatement(), sqlStatementsSource().params(lockConfiguration));
        logger.debug("Extending lock={} until={}", lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());
        try {
            int updated = executeUpdate(stmt.sql(), stmt.parameters());
            return updated > 0;
        } catch (Exception e) {
            throw new LockException("Unexpected exception when unlocking", unwrap(e));
        }
    }

    @Override
    public void unlock(LockConfiguration lockConfiguration) {
        NamedSql.Statement stmt = translate(sqlStatementsSource().getUnlockStatement(), sqlStatementsSource().params(lockConfiguration));
        try {
            executeUpdate(stmt.sql(), stmt.parameters());
        } catch (Exception e) {
            throw new LockException("Unexpected exception when unlocking", unwrap(e));
        }
    }

    private SqlStatementsSource sqlStatementsSource() {
        SqlStatementsSource local = sqlStatementsSource;
        if (local == null) {
            synchronized (this) {
                if (sqlStatementsSource == null) {
                    sqlStatementsSource = SqlStatementsSource.create(configuration);
                }
                local = sqlStatementsSource;
            }
        }
        return local;
    }

    private int executeUpdate(String sql, List<Object> params) {
        Tuple tuple = toTuple(params);
        try {
            // block to keep compatibility with synchronous ShedLock contracts
            RowSet<?> rs = pool.preparedQuery(sql).execute(tuple).toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
            return rs.rowCount();
        } catch (CompletionException ce) {
            // unwrap
            Throwable cause = ce.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Tuple toTuple(List<Object> params) {
        Tuple t = Tuple.tuple();
        for (Object p : params) {
            if (p instanceof Calendar cal) {
                OffsetDateTime odt = OffsetDateTime.ofInstant(cal.toInstant(), ZoneId.systemDefault());
                t.addValue(odt);
            } else {
                t.addValue(p);
            }
        }
        return t;
    }

    private static Throwable unwrap(Throwable e) {
        if (e instanceof CompletionException && e.getCause() != null) {
            return e.getCause();
        }
        return e;
    }
}
