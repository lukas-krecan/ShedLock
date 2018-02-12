package net.javacrumbs.shedlock.test.support.jdbc;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.FuzzTester;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.concurrent.ExecutionException;

public class TransactionalFuzzTester {

    public static void fuzzTestShouldWorkWithTransaction(LockProvider lockProvider, DataSource dataSource) throws ExecutionException, InterruptedException {
        new FuzzTester(lockProvider) {
            @Override
            protected Void task(int iterations) {
                TransactionTemplate transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
                return transactionTemplate.execute(status -> super.task(iterations));
            }

            @Override
            protected boolean shouldLog() {
                return true;
            }
        }.doFuzzTest();
    }
}
