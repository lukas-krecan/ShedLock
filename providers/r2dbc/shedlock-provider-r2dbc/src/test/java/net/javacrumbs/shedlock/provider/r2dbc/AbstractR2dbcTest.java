package net.javacrumbs.shedlock.provider.r2dbc;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractJdbcLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;

import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractR2dbcTest extends AbstractJdbcLockProviderIntegrationTest {

    private ConnectionFactory connectionFactory;

    @BeforeAll
    public void startDb() {
        getDbConfig().startDb();

        ConnectionFactory cf = ConnectionFactories.get(
            ConnectionFactoryOptions
                .parse(getDbConfig().getR2dbcUrl())
                .mutate()
                .option(USER, getDbConfig().getUsername())
                .option(PASSWORD, getDbConfig().getPassword())
                .build()
        );

        if (usePool()) {

            ConnectionPoolConfiguration configuration = ConnectionPoolConfiguration.builder(cf)
                .maxIdleTime(Duration.ofMillis(1000))
                .maxSize(20)
                .build();

            connectionFactory = new ConnectionPool(configuration);
        } else {
            // Oracle does not support pool - https://github.com/oracle/oracle-r2dbc/issues/29
            connectionFactory = cf;
        }
    }

    @AfterAll
    public void shutDownDb() {
        getDbConfig().shutdownDb();
    }

    @Override
    protected boolean useDbTime() {
        return false;
    }

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return new R2dbcLockProvider(connectionFactory());
    }

    protected ConnectionFactory connectionFactory() {
        return connectionFactory;
    }

    protected boolean usePool() {
        return true;
    }
}
