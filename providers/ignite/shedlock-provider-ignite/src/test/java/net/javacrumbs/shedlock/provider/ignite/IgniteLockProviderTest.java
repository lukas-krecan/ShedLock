/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.ignite;

import static net.javacrumbs.shedlock.provider.ignite.IgniteLockProvider.DEFAULT_SHEDLOCK_CACHE_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractExtensibleLockProviderIntegrationTest;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteServer;
import org.apache.ignite.InitParameters;
import org.apache.ignite.table.KeyValueView;
import org.apache.ignite.table.Table;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/** Test for {@link IgniteLockProvider}. */
public class IgniteLockProviderTest extends AbstractExtensibleLockProviderIntegrationTest {
    private static Ignite ignite;
    private static KeyValueView<String, LockValue> keyValueView;
    private static Table table;
    private static IgniteServer node;
    private static final String TEST_NODE_NAME = "ignite-node";

    @BeforeAll
    public static void startIgnite(@TempDir Path tempDir) {
        try {
            node = IgniteServer.start(
                    TEST_NODE_NAME,
                    Paths.get(Thread.currentThread()
                            .getContextClassLoader()
                            .getResource("ignite-config.conf")
                            .toURI()),
                    tempDir);

            InitParameters initParameters = InitParameters.builder()
                    .clusterName("cluster")
                    .metaStorageNodeNames(TEST_NODE_NAME)
                    .build();

            node.initCluster(initParameters);

            ignite = node.api();

            table = ignite.tables().table(DEFAULT_SHEDLOCK_CACHE_NAME);
            if (table == null) {
                ignite.sql()
                        .execute(
                                null,
                                "CREATE TABLE IF NOT EXISTS " + DEFAULT_SHEDLOCK_CACHE_NAME + " ("
                                        + "name VARCHAR PRIMARY KEY, "
                                        + "locked_at TIMESTAMP, "
                                        + "lock_until TIMESTAMP, "
                                        + "locked_by VARCHAR"
                                        + ")");
                table = ignite.tables().table(DEFAULT_SHEDLOCK_CACHE_NAME);
            }
            keyValueView = table.keyValueView(String.class, LockValue.class);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to initialize Ignite", e);
        }
    }

    @AfterAll
    public static void stopIgnite() {
        if (node != null) {
            node.shutdown();
        }
    }

    @BeforeEach
    public void cleanDb() {
        ignite.sql().execute(null, "DELETE FROM " + DEFAULT_SHEDLOCK_CACHE_NAME);
    }

    @Override
    protected ExtensibleLockProvider getLockProvider() {
        return new IgniteLockProvider(ignite);
    }

    @Override
    protected void assertUnlocked(String lockName) {
        LockValue val = keyValueView.get(null, lockName);

        LocalDateTime now = now();

        assertThat(val).isNotNull();
        assertThat(val.getLockUntil()).isBeforeOrEqualTo(now);
        assertThat(val.getLockedAt()).isBeforeOrEqualTo(now);
        assertThat(val.getLockedBy()).isNotEmpty();
    }

    private static LocalDateTime now() {
        return Instant.now().atZone(ZoneId.of("UTC")).toLocalDateTime();
    }

    @Override
    protected void assertLocked(String lockName) {
        LockValue val = keyValueView.get(null, lockName);

        LocalDateTime now = now();

        assertThat(val).isNotNull();
        assertThat(val.getLockUntil()).isAfter(now);
        assertThat(val.getLockedAt()).isBeforeOrEqualTo(now);
        assertThat(val.getLockedBy()).isNotEmpty();
    }
}
