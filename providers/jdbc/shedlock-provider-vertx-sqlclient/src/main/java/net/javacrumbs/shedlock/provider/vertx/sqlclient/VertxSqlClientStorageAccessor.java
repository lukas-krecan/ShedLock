package net.javacrumbs.shedlock.provider.vertx.sqlclient;

import static java.util.stream.Collectors.toUnmodifiableMap;

import io.vertx.sqlclient.DatabaseException;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.templates.SqlTemplate;
import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.sql.SqlStatementsSource;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.LockException;

class VertxSqlClientStorageAccessor extends AbstractStorageAccessor {
    private static final Pattern NAMED_PARAMETER_PATTERN = Pattern.compile(":[a-zA-Z]+");
    private final SqlClient sqlClient;

    private final SqlStatementsSource sqlStatementsSource;

    VertxSqlClientStorageAccessor(VertxSqlClientLockProvider.Configuration configuration) {
        this.sqlClient = configuration.getSqlClient();
        this.sqlStatementsSource = SqlStatementsSource.create(configuration);
    }

    @Override
    public boolean insertRecord(LockConfiguration lockConfiguration) {
        String stmt = translate(sqlStatementsSource().getInsertStatement());
        try {
            int updated = executeUpdate(stmt, lockConfiguration);
            return updated > 0;
        } catch (Exception e) {
            Throwable cause = unwrap(e);
            if (cause instanceof DatabaseException dbException) {
                if ("23000".equals(dbException.getSqlState())) {
                    // Duplicate key
                    return false;
                }
            }
            logger.debug("Exception thrown when inserting record", cause);
            throw new LockException("Unexpected exception when locking", cause);
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
        return sqlStatementsSource;
    }

    private int executeUpdate(String sql, LockConfiguration lockConfiguration)
            throws ExecutionException, InterruptedException, TimeoutException {
        Map<String, Object> params = translateParams(sqlStatementsSource().params(lockConfiguration));
        // block to keep compatibility with synchronous ShedLock contracts
        RowSet<?> rs = SqlTemplate.forQuery(sqlClient, sql)
                .execute(params)
                .toCompletionStage()
                .toCompletableFuture()
                .get(30, TimeUnit.SECONDS);
        return rs.rowCount();
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
        if ((e instanceof CompletionException || e instanceof ExecutionException) && e.getCause() != null) {
            return e.getCause();
        }
        return e;
    }
}
