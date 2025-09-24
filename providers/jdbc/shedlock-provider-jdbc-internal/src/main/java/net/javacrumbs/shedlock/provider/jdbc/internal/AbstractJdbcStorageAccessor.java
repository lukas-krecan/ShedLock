/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.jdbc.internal;

import static java.sql.Types.TIMESTAMP;
import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.provider.jdbc.internal.NamedSqlTranslator.translate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.function.BiFunction;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.provider.jdbc.internal.NamedSqlTranslator.SqlStatement;
import net.javacrumbs.shedlock.provider.sql.SqlConfiguration;
import net.javacrumbs.shedlock.provider.sql.SqlStatementsSource;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.LockException;
import org.jspecify.annotations.Nullable;

/** Internal class, please do not use. */
public abstract class AbstractJdbcStorageAccessor extends AbstractStorageAccessor {
    private final SqlConfiguration configuration;
    private @Nullable SqlStatementsSource sqlStatementsSource;

    public AbstractJdbcStorageAccessor(SqlConfiguration configuration) {
        this.configuration = requireNonNull(configuration, "Configuration is null");
    }

    @Override
    public boolean insertRecord(LockConfiguration lockConfiguration) {
        // Try to insert if the record does not exist (not optimal, but the simplest
        // platform agnostic
        // way)
        SqlStatementsSource sqlStatementsSource = sqlStatementsSource();
        String sql = sqlStatementsSource.getInsertStatement();
        SqlStatement sqlStatement = translate(sql, sqlStatementsSource.params(lockConfiguration));
        return executeCommand(
                sqlStatement.sql(),
                statement -> {
                    setParameters(statement, sqlStatement.parameters());
                    int insertedRows = statement.executeUpdate();
                    return insertedRows > 0;
                },
                this::handleInsertionException);
    }

    @Override
    public boolean updateRecord(LockConfiguration lockConfiguration) {
        SqlStatementsSource sqlStatementsSource = sqlStatementsSource();
        String sql = sqlStatementsSource.getUpdateStatement();
        SqlStatement sqlStatement = translate(sql, sqlStatementsSource.params(lockConfiguration));
        return executeCommand(
                sqlStatement.sql(),
                statement -> {
                    setParameters(statement, sqlStatement.parameters());
                    int updatedRows = statement.executeUpdate();
                    return updatedRows > 0;
                },
                this::handleUpdateException);
    }

    @Override
    public boolean extend(LockConfiguration lockConfiguration) {
        SqlStatementsSource sqlStatementsSource = sqlStatementsSource();
        String sql = sqlStatementsSource.getExtendStatement();
        SqlStatement sqlStatement = translate(sql, sqlStatementsSource.params(lockConfiguration));

        logger.debug("Extending lock={} until={}", lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());

        return executeCommand(
                sqlStatement.sql(),
                statement -> {
                    setParameters(statement, sqlStatement.parameters());
                    return statement.executeUpdate() > 0;
                },
                this::handleUnlockException);
    }

    @Override
    public void unlock(LockConfiguration lockConfiguration) {
        SqlStatementsSource sqlStatementsSource = sqlStatementsSource();
        String sql = sqlStatementsSource.getUpdateStatement();
        SqlStatement sqlStatement = translate(sql, sqlStatementsSource.params(lockConfiguration));

        executeCommand(
                sqlStatement.sql(),
                statement -> {
                    setParameters(statement, sqlStatement.parameters());
                    statement.executeUpdate();
                    return null;
                },
                this::handleUnlockException);
    }

    protected abstract <T> T executeCommand(
            String sql, SqlFunction<PreparedStatement, T> body, BiFunction<String, SQLException, T> exceptionHandler);

    boolean handleInsertionException(String sql, SQLException e) {
        if (e instanceof SQLIntegrityConstraintViolationException) {
            // lock record already exists
        } else {
            // can not throw exception here, some drivers (Postgres) do not throw
            // SQLIntegrityConstraintViolationException on duplicate key
            // we will try update in the next step, so if there is another problem, an
            // exception will be
            // thrown there
            logger.debug("Exception thrown when inserting record", e);
        }
        return false;
    }

    private SqlStatementsSource sqlStatementsSource() {
        synchronized (configuration) {
            if (sqlStatementsSource == null) {
                sqlStatementsSource = SqlStatementsSource.create(configuration);
            }
            return sqlStatementsSource;
        }
    }

    private static void setParameters(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            Object value = parameters.get(i);
            if (value instanceof Calendar) {
                // FIXME: Zones
                value = new Timestamp(((Calendar) value).getTimeInMillis());
            }
            statement.setObject(i + 1, value);

        }
    }

    boolean handleUpdateException(String sql, SQLException e) {
        logger.debug("Unexpected exception when updating lock record", e);
        throw new LockException("Unexpected exception when locking", e);
    }

    boolean handleUnlockException(String sql, SQLException e) {
        throw new LockException("Unexpected exception when unlocking", e);
    }

    @FunctionalInterface
    public interface SqlFunction<T, R> {
        R apply(T t) throws SQLException;
    }
}
