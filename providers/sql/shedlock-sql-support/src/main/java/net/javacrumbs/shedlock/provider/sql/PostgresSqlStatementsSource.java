package net.javacrumbs.shedlock.provider.sql;

class PostgresSqlStatementsSource extends SqlStatementsSource {
    PostgresSqlStatementsSource(SqlConfiguration configuration) {
        super(configuration);
    }

    @Override
    public String getInsertStatement() {
        return super.getInsertStatement() + " ON CONFLICT (" + name() + ") DO UPDATE " + "SET " + lockUntil()
                + " = :lockUntil, " + lockedAt() + " = :now, " + lockedBy() + " = :lockedBy " + "WHERE " + tableName()
                + "." + lockUntil() + " <= :now";
    }
}
