package net.javacrumbs.shedlock.provider.jdbc;

import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractJdbcLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractJdbcTest extends AbstractJdbcLockProviderIntegrationTest {
    @BeforeAll
    public void startDb() throws IOException {
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
        return new JdbcLockProvider(testUtils.getDatasource());
    }
}
