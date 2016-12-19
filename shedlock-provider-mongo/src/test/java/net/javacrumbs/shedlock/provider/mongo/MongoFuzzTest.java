/**
 * Copyright 2009-2016 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.mongo;

import de.flapdoodle.embed.mongo.tests.MongodForTestsFactory;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractFuzzTest;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.IOException;
import java.net.UnknownHostException;

public class MongoFuzzTest extends AbstractFuzzTest {
    private static MongodForTestsFactory mongoFactory;

    private LockProvider lockProvider;

    @Before
    public void createLockProvider() throws UnknownHostException {
        lockProvider = new MongoLockProvider(mongoFactory.newMongo(), "test", "test");
    }


    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    @BeforeClass
    public static void startMongo() throws IOException {
        mongoFactory = new MongodForTestsFactory();
    }

    @AfterClass
    public static void stopMongo() throws IOException {
        mongoFactory.shutdown();
    }
}
