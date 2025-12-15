package net.javacrumbs.shedlock.provider.sql;

/**
 * CockroachDb does not support make_interval, vert.x does not support cast(? as interval). Hopefully we don't
 * have to combine CockroachDB and Vert.x.
 */
class CockroachDbSqlServerTimeStatementsSource extends PostgresSqlServerTimeStatementsSource {
    private static final String now = "timezone('utc', CURRENT_TIMESTAMP)";
    private static final String lockAtMostFor = now + " + cast(:lockAtMostForInterval as interval)";

    CockroachDbSqlServerTimeStatementsSource(SqlConfiguration configuration) {
        super(configuration);
    }

    @Override
    protected String lockAtMostFor() {
        return lockAtMostFor;
    }

    @Override
    protected String lockAtLeastFor() {
        return "cast(:lockAtLeastForInterval as interval)";
    }
}
