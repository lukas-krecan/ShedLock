package net.javacrumbs.shedlock.provider.r2dbc;

import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.time.Duration;
import net.javacrumbs.shedlock.provider.sql.DatabaseProduct;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractJdbcLockProviderIntegrationTest;
import net.javacrumbs.shedlock.test.support.jdbc.DbConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractR2dbcTest {
    private final DbConfig dbConfig;

    private ConnectionFactory connectionFactory;

    protected AbstractR2dbcTest(DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @BeforeAll
    public void startDb() {
        dbConfig.startDb();

        ConnectionFactory cf = ConnectionFactories.get(ConnectionFactoryOptions.parse(dbConfig.getR2dbcUrl())
                .mutate()
                .option(USER, dbConfig.getUsername())
                .option(PASSWORD, dbConfig.getPassword())
                .build());

        ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration.builder(cf)
                .maxIdleTime(Duration.ofSeconds(1))
                .maxSize(20)
                .build();

        connectionFactory = new ConnectionPool(configuration);
    }

    @AfterAll
    public void shutDownDb() {
        dbConfig.shutdownDb();
    }

    @Nested
    class ClientTime extends AbstractJdbcLockProviderIntegrationTest {
        @Override
        protected DbConfig getDbConfig() {
            return dbConfig;
        }

        @Override
        protected StorageBasedLockProvider getLockProvider() {
            return new R2dbcLockProvider(R2dbcLockProvider.Configuration.builder(connectionFactory)
                    .withDatabaseProduct(databaseProduct())
                    .build());
        }

        @Override
        protected boolean useDbTime() {
            return false;
        }
    }

    @Nested
    class DbTime extends AbstractJdbcLockProviderIntegrationTest {
        @Override
        protected DbConfig getDbConfig() {
            return dbConfig;
        }

        @Override
        protected StorageBasedLockProvider getLockProvider() {
            return new R2dbcLockProvider(R2dbcLockProvider.Configuration.builder(connectionFactory)
                    .withDatabaseProduct(databaseProduct())
                    .usingDbTime()
                    .build());
        }

        @Override
        protected boolean useDbTime() {
            return true;
        }
    }

    protected abstract DatabaseProduct databaseProduct();
}
