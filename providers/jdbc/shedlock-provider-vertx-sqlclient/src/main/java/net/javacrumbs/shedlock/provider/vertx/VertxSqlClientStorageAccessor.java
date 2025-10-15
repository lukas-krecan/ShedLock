package net.javacrumbs.shedlock.provider.vertx;

import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.toUnmodifiableMap;

import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.templates.SqlTemplate;
import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.sql.SqlStatementsSource;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.LockException;
import org.jspecify.annotations.Nullable;

class VertxSqlClientStorageAccessor extends AbstractStorageAccessor {
    private static final Pattern NAMED_PARAMETER_PATTERN = Pattern.compile(":[a-zA-Z]+");

    private final VertxSqlClientLockProvider.Configuration configuration;
    private final SqlClient sqlClient;

    @Nullable
    private SqlStatementsSource sqlStatementsSource;

    VertxSqlClientStorageAccessor(VertxSqlClientLockProvider.Configuration configuration) {
        this.configuration = configuration;
        this.sqlClient = configuration.getSqlClient();
    }

    @Override
    public boolean insertRecord(LockConfiguration lockConfiguration) {
        String stmt = translate(sqlStatementsSource().getInsertStatement());
        try {
            int updated = executeUpdate(stmt, lockConfiguration);
            return updated > 0;
        } catch (Exception e) {
            // same semantics as JDBC implementation - ignore errors on insert and return false
            logger.debug("Exception thrown when inserting record", e);
            return false;
        }
    }

    @Override
    public boolean updateRecord(LockConfiguration lockConfiguration) {
        String stmt = translate(sqlStatementsSource().getUpdateStatement());
        try {
            int updated = executeUpdate(stmt, lockConfiguration);
            return updated > 0;
        } catch (Exception e) {
            logger.debug("Unexpected exception when updating lock record", e);
            throw new LockException("Unexpected exception when locking", unwrap(e));
        }
    }

    @Override
    public boolean extend(LockConfiguration lockConfiguration) {
        String stmt = translate(sqlStatementsSource().getExtendStatement());
        logger.debug("Extending lock={} until={}", lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());
        try {
            int updated = executeUpdate(stmt, lockConfiguration);
            return updated > 0;
        } catch (Exception e) {
            throw new LockException("Unexpected exception when unlocking", unwrap(e));
        }
    }

    @Override
    public void unlock(LockConfiguration lockConfiguration) {
        String stmt = translate(sqlStatementsSource().getUnlockStatement());
        try {
            executeUpdate(stmt, lockConfiguration);
        } catch (Exception e) {
            throw new LockException("Unexpected exception when unlocking", unwrap(e));
        }
    }

    private String translate(String statement) {
        return NAMED_PARAMETER_PATTERN
                .matcher(statement)
                .replaceAll(result -> "#{" + result.group().substring(1) + "}");
    }

    private SqlStatementsSource sqlStatementsSource() {
        synchronized (configuration) {
            if (sqlStatementsSource == null) {
                sqlStatementsSource = SqlStatementsSource.create(configuration);
            }
            return sqlStatementsSource;
        }
    }

    private int executeUpdate(String sql, LockConfiguration lockConfiguration) {
        Map<String, Object> params = translateParams(sqlStatementsSource().params(lockConfiguration));
        try {
            // block to keep compatibility with synchronous ShedLock contracts
            RowSet<?> rs = SqlTemplate.forQuery(sqlClient, sql)
                    .execute(params)
                    .toCompletionStage()
                    .toCompletableFuture()
                    .get(30, TimeUnit.SECONDS);
            return rs.rowCount();
        } catch (CompletionException ce) {
            Throwable cause = ce.getCause();
            throw new LockException(requireNonNullElse(cause, ce));
        } catch (Exception e) {
            throw new LockException(e);
        }
    }

    private Map<String, Object> translateParams(Map<String, Object> params) {
        return params.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), translate(entry.getValue())))
                .collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Object translate(Object value) {
        if (value instanceof Calendar cal) {
            return cal.toInstant().atZone(cal.getTimeZone().toZoneId()).toLocalDateTime();
        } else {
            return value;
        }
    }

    private static Throwable unwrap(Throwable e) {
        if (e instanceof CompletionException && e.getCause() != null) {
            return e.getCause();
        }
        return e;
    }
}
