/**
 * Copyright 2009-2017 the original author or authors.
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
package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractMySqlJdbcLockProviderIntegrationTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

@RunWith(Parameterized.class)
public class MySqlJdbcTemplateLockProviderIntegrationTest extends AbstractMySqlJdbcLockProviderIntegrationTest {

    private LockProviderFactory lockProviderFactory;

    public MySqlJdbcTemplateLockProviderIntegrationTest(LockProviderFactory lockProviderFactory) {
        this.lockProviderFactory = lockProviderFactory;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> lockProviders() {
        return LockProviderFactory.lockProviders();
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProviderFactory.createLockProvider(getDatasource());
    }
}