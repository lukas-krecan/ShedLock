package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;

class SqlStatementsSource {
    private final Configuration configuration;

    private static final Logger logger = LoggerFactory.getLogger(SqlStatementsSource.class);

    SqlStatementsSource(Configuration configuration) {
        this.configuration = configuration;
    }

    static SqlStatementsSource create(Configuration configuration) {
        String databaseProductName = getDatabaseProductName(configuration);
        if ("PostgreSQL".equals(databaseProductName)) {
            logger.debug("Using PostgresSqlStatementsSource");
            return new PostgresSqlStatementsSource(configuration);
        } else {
            logger.debug("Using SqlStatementsSource");
            return new SqlStatementsSource(configuration);
        }
    }

    private static String getDatabaseProductName(Configuration configuration) {
        return configuration.getJdbcTemplate().execute((ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
    }


    String getInsertStatement() {
        return "INSERT INTO " + tableName() + "(" + name() + ", " + lockUntil() + ", " + lockedAt() + ", " + lockedBy() + ") VALUES(?, ?, ?, ?)";
    }


    public String getUpdateStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = ?, " + lockedAt() + " = ?, " + lockedBy() + " = ? WHERE " + name() + " = ? AND " + lockUntil() + " <= ?";
    }

    public String getExtendStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = ? WHERE " + name() + " = ? AND " + lockedBy() + " = ? AND " + lockUntil() + " > ?";
    }

    public String getUnlockStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = ? WHERE " + name() + " = ?";
    }

    String name() {
        return configuration.getColumnNames().getName();
    }

    private String lockUntil() {
        return configuration.getColumnNames().getLockUntil();
    }

    private String lockedAt() {
        return configuration.getColumnNames().getLockedAt();
    }

    private String lockedBy() {
        return configuration.getColumnNames().getLockedBy();
    }

    private String tableName() {
        return configuration.getTableName();
    }
}

class PostgresSqlStatementsSource extends SqlStatementsSource {
    PostgresSqlStatementsSource(Configuration configuration) {
        super(configuration);
    }

    @Override
    String getInsertStatement() {
        return super.getInsertStatement() + " ON CONFLICT (" + name() + ") DO NOTHING";
    }
}
