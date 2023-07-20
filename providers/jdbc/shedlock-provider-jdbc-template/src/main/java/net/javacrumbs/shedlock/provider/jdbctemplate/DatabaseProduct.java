package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider.Configuration;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;

public enum DatabaseProduct {
    POSTGRES_SQL("PostgreSQL"::equals, PostgresSqlServerTimeStatementsSource::new),
    SQL_SERVER("Microsoft SQL Server"::equals, MsSqlServerTimeStatementsSource::new),
    ORACLE("Oracle"::equals, OracleServerTimeStatementsSource::new),
    MY_SQL("MySQL"::equals, MySqlServerTimeStatementsSource::new),
    MARIA_DB("MariaDB"::equals, MySqlServerTimeStatementsSource::new),
    HQL("HSQL Database Engine"::equals, HsqlServerTimeStatementsSource::new),
    H2("H2"::equals, H2ServerTimeStatementsSource::new),
    DB2(s -> s.startsWith("DB2"), Db2ServerTimeStatementsSource::new),
    UNKNOWN(s -> false, configuration -> {
        throw new UnsupportedOperationException("DB time is not supported for unknown database product");
    });

    private final Predicate<String> productMatcher;

    private final Function<Configuration, SqlStatementsSource> serverTimeStatementsSource;

    DatabaseProduct(Predicate<String> productMatcher, Function<Configuration, SqlStatementsSource> serverTimeStatementsSource) {
        this.productMatcher = productMatcher;
        this.serverTimeStatementsSource = serverTimeStatementsSource;
    }

    SqlStatementsSource getDbTimeStatementSource(Configuration configuration) {
        return serverTimeStatementsSource.apply(configuration);
    }

    /**
     * Searches for the right DatabaseProduct based on the ProductName returned from JDBC Connection Metadata
     *
     * @param productName Obtained from the JDBC connection. See java.sql.connection.getMetaData().getProductName().
     * @return The matching ProductName enum
     */
    @NonNull
    static DatabaseProduct matchProductName(final String productName) {
        return Arrays.stream(DatabaseProduct.values())
            .filter(databaseProduct -> databaseProduct.productMatcher.test(productName))
            .findFirst().orElse(UNKNOWN);
    }
}

