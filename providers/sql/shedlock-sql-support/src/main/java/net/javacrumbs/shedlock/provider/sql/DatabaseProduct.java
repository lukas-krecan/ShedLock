package net.javacrumbs.shedlock.provider.sql;

import org.jspecify.annotations.Nullable;

public enum DatabaseProduct {
    POSTGRES_SQL,
    SQL_SERVER,
    ORACLE,
    MY_SQL,
    MARIA_DB,
    HQL,
    H2,
    DB2,
    COCKROACH_DB,
    UNKNOWN;

    /**
     * Searches for the right DatabaseProduct based on the ProductName returned from
     * JDBC Connection Metadata
     *
     * @param jdbcProductName Obtained from the JDBC connection. See java.sql.connection.getMetaData().getProductName().
     * @return The matching ProductName enum
     */
    public static DatabaseProduct matchProductName(@Nullable String jdbcProductName) {
        if (jdbcProductName == null) {
            return UNKNOWN;
        }
        if ("PostgreSQL".equalsIgnoreCase(jdbcProductName)) {
            return POSTGRES_SQL;
        }
        if ("Microsoft SQL Server".equalsIgnoreCase(jdbcProductName)) {
            return SQL_SERVER;
        }
        if ("Oracle".equalsIgnoreCase(jdbcProductName)) {
            return ORACLE;
        }
        if ("MySQL".equalsIgnoreCase(jdbcProductName)) {
            return MY_SQL;
        }
        if ("MariaDB".equalsIgnoreCase(jdbcProductName)) {
            return MARIA_DB;
        }
        if ("HSQL Database Engine".equalsIgnoreCase(jdbcProductName)) {
            return HQL;
        }
        if ("H2".equalsIgnoreCase(jdbcProductName)) {
            return H2;
        }
        if (jdbcProductName.regionMatches(true, 0, "DB2", 0, 3)) {
            return DB2;
        }
        return UNKNOWN;
    }
}
