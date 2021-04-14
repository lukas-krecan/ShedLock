/**
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
            protected Void task(int iterations, Job job) {
                TransactionTemplate transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
                return transactionTemplate.execute(status -> super.task(iterations, job));
            }

            @Override
            protected boolean shouldLog() {
                return true;
            }
        }.doFuzzTest();
    }
}
