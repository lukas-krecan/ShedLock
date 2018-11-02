/**
 * Copyright 2009-2018 the original author or authors.
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
package net.javacrumbs.shedlock.provider.dynamodb;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.javacrumbs.shedlock.provider.dynamodb.DynamodbLockProvider.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * To run DynamoLockProviderIntegrationTest, a mock implementation of dynamodb is run locally.
 * This mock version is called dynamodb local. To run dynamodb local, dependencies are
 * assembled using maven build
 *
 * Before running this DynamoLockProviderIntegrationTest manually, outside maven environment
 * run maven build once so that dependencies are arranged in a way that dynamodb local
 * can be started up as a pre requisite for this test.
 *
 * Following are the pre-requisites to run Dynamodb local -
 *
 * Dynamodb local requires aws cli to be installed locally.
 * <pre>https://docs.aws.amazon.com/cli/latest/userguide/installing.html</pre>
 *
 * Run the command
 * <pre>aws configure</pre>
 *
 * Select any value or defaults for AWS Access Key ID and AWS Secret Access Key
 * Use us-west-2 for region
 * Select defaults for remaining
 */
public class DynamoLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DynamoLockProviderIntegrationTest.class);

    private static final String TABLE_NAME = "shedLock";
    private static final String REGION = "us-west-2";
    private static AmazonDynamoDB amazonDynamoDB;
    private static DynamodbLockProvider dynamodbLockProvider;
    private static Process dynamodbLocalProcess;
    private static String dynamodbHost = "localhost";
    private static int dynamodbPort = 8776;
    private static String NODE_NAME = DynamodbLockProvider.NODE_NAME;

    @Override
    protected LockProvider getLockProvider() {
        return dynamodbLockProvider;
    }

    @Override
    protected void assertUnlocked(String lockName) {
        Map<String, Object> lockDocument = getLockDocument(lockName);
        if (lockDocument != null) {
            Instant lockUntill = Instant.ofEpochMilli(Long.valueOf(lockDocument.get(LOCK_UNTIL).toString()));
            Instant lockedAt = Instant.ofEpochMilli(Long.valueOf(lockDocument.get(LOCKED_AT).toString()));
            assertTrue(lockUntill.isBefore(Instant.now()) || lockUntill.equals(Instant.now()));
            assertTrue(lockedAt.isBefore(Instant.now()) || lockedAt.equals(Instant.now()));
            assertEquals(NODE_NAME, lockDocument.get(LOCKED_BY));
        }
    }

    private Date now() {
        return new Date();
    }

    @Override
    protected void assertLocked(String lockName) {
        Map<String, Object> lockDocument = getLockDocument(lockName);
        Instant lockUntill = Instant.ofEpochMilli(Long.valueOf(lockDocument.get(LOCK_UNTIL).toString()));
        Instant lockedAt = Instant.ofEpochMilli(Long.valueOf(lockDocument.get(LOCKED_AT).toString()));
        assertTrue(lockUntill.isAfter(Instant.now()));
        assertTrue(lockedAt.isBefore(Instant.now()) || lockedAt.equals(Instant.now()));
        assertEquals(NODE_NAME, lockDocument.get(LOCKED_BY));
    }

    private Map<String, Object> getLockDocument(String lockName) {
        DynamoDB dynamoDB = new DynamoDB(amazonDynamoDB);
        Table table = dynamoDB.getTable(TABLE_NAME);
        GetItemSpec getItemSpec = new GetItemSpec().withPrimaryKey(NAME, lockName);
        Item outcome = table.getItem(getItemSpec);
        if (outcome != null) {
            return outcome.asMap();
        } else {
            return null;
        }
    }

    @BeforeClass
    public static void start() throws Exception {
        dynamodbLocalProcess = startDynamoDbLocalProcess();
        amazonDynamoDB = AmazonDynamoDBClientBuilder.standard().withEndpointConfiguration(
                // we can use any region here
                new AwsClientBuilder.EndpointConfiguration("http://" + dynamodbHost + ":" + dynamodbPort, REGION))
                .build();
    }

    private static Process startDynamoDbLocalProcess() throws IOException {
        Path depsDir = Paths.get(".", "target", "dependencies");
        String classpathDeps = Arrays.stream(depsDir.toFile().listFiles())
                .map(File::getAbsolutePath)
                .collect(Collectors.joining(":"));
        ProcessBuilder pb = new ProcessBuilder("java", "-classpath", classpathDeps,
                "-Dsqlite4java.library.path=" + depsDir.toFile().getAbsolutePath(),
                "com.amazonaws.services.dynamodbv2.local.main.ServerRunner", "inMemory", "-port", String.valueOf(dynamodbPort), "-sharedDb");

        Process dynamoLocalProcess = pb.start();
        byte[] buffer = new byte[500];
        dynamoLocalProcess.getErrorStream().read(buffer);
        log.error(new String(buffer));

        buffer = new byte[500];
        dynamoLocalProcess.getInputStream().read(buffer);
        log.info(new String(buffer));

        return dynamoLocalProcess;
    }


    @AfterClass
    public static void stop() throws IOException {

        if(amazonDynamoDB != null) {
            amazonDynamoDB.shutdown();
        }

        stopDynamoDbLocalProcess(dynamodbLocalProcess);

        Path dbFile = Paths.get(".", "shared-local-instance.db");
        dbFile.toFile().delete();



    }

    private static void stopDynamoDbLocalProcess(Process dynamoDbLocalProcess) {
        dynamoDbLocalProcess.destroy();
        try {
            dynamoDbLocalProcess.waitFor(2, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            dynamoDbLocalProcess.destroyForcibly();
        }

        if (dynamoDbLocalProcess.isAlive()) {
            dynamoDbLocalProcess.destroyForcibly();
        }
    }

    @Before
    public void createLockProvider() throws UnknownHostException {
        boolean isTableExists= amazonDynamoDB.listTables()
                .getTableNames().stream()
                .filter(table -> table.equalsIgnoreCase(TABLE_NAME))
                .findFirst().isPresent();
        if (isTableExists) {
            amazonDynamoDB.deleteTable(TABLE_NAME);
        }
        dynamodbLockProvider = new DynamodbLockProvider(amazonDynamoDB);
    }


}