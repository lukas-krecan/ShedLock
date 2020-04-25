package net.javacrumbs.shedlock.provider.jdbctemplate;

class PostgresSqlStatementsSource extends SqlStatementsSource {
    PostgresSqlStatementsSource(JdbcTemplateLockProvider.Configuration configuration) {
        super(configuration);
    }

    @Override
    String getInsertStatement() {
        return super.getInsertStatement() + " ON CONFLICT (" + name() + ") DO UPDATE " +
            "SET " + lockUntil() + " = :lockUntil, " + lockedAt() + " = :now, " + lockedBy() + " = :lockedBy " +
            "WHERE " + tableName() + "." + lockUntil() + " <= :now";
    }

}
