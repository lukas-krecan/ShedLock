package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider.Configuration;

class SqlStatementsSource {
    private final Configuration configuration;

    SqlStatementsSource(Configuration configuration) {
        this.configuration = configuration;
    }

    public static SqlStatementsSource create(Configuration configuration) {
        return new SqlStatementsSource(configuration);
    }


    String getInsertStatement() {
        return "INSERT INTO " + tableName() + "(" + name() + ", " + lockUntil() + ", " + lockedAt() + ", " + lockedBy() + ") VALUES(?, ?, ?, ?)";
    }


    public String getUpdateStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = ?, " + lockedAt() + " = ?, " + lockedBy() + " = ? WHERE " + name() + " = ? AND " + lockUntil() + " <= ?";
    }

    public String getExtendStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = ? WHERE " + name() + " = ? AND " + lockedBy() + " = ? AND " + lockUntil() + " > ? ";
    }

    public String getUnlockStatement() {
        return "UPDATE " + tableName() + " SET " + lockUntil() + " = ? WHERE " + name() + " = ?";
    }

    private String name() {
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
