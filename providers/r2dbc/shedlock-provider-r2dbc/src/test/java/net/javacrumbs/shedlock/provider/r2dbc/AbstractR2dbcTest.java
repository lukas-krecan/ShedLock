package net.javacrumbs.shedlock.provider.r2dbc;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractJdbcLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractR2dbcTest extends AbstractJdbcLockProviderIntegrationTest {

    private ConnectionFactory connectionFactory;

    @BeforeAll
    public void startDb() {
        getDbConfig().startDb();
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
        if (connectionFactory == null) {
            connectionFactory = ConnectionFactories.get(
                ConnectionFactoryOptions
                    .parse(getDbConfig().getJdbcUrl().replace("jdbc", "r2dbc"))
                    .mutate()
                    .option(USER, getDbConfig().getUsername())
                    .option(PASSWORD, getDbConfig().getPassword())
                    .build()
            );
        }
        return connectionFactory;
    }
}
