package net.javacrumbs.shedlock.provider.jdbctemplate;

import java.util.Arrays;
import java.util.function.Predicate;

public enum DatabaseProduct {

    PostgresSQL("PostgreSQL"::equals),
    SQLServer("Microsoft SQL Server"::equals),
    Oracle("Oracle"::equals),
    MySQL("MySQL"::equals),
    MariaDB("MariaDB"::equals),
    HQL("HSQL Database Engine"::equals),
    H2("H2"::equals),
    DB2(s -> s.startsWith("DB2")),
    Unknown(s -> false);

    private final Predicate<String> productMatcher;

    DatabaseProduct(Predicate<String> productMatcher) {
        this.productMatcher = productMatcher;
    }

    /**
     * Searches for the right DatabaseProduct based on the ProductName returned from JDBC Connection Metadata
     *
     * @param productName Obtained from the JDBC connection. See java.sql.connection.getMetaData().getProductName().
     * @return The matching ProductName enum
     */
    public static DatabaseProduct matchProductName(final String productName) {
        return Arrays.stream(DatabaseProduct.values())
            .filter(databaseProduct -> databaseProduct.productMatcher.test(productName))
            .findFirst().orElse(null);
    }
}

