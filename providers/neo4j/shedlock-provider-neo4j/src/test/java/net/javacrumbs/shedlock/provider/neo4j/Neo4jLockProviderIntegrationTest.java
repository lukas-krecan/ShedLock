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
package net.javacrumbs.shedlock.provider.neo4j;

import org.junit.jupiter.api.BeforeAll;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class Neo4jLockProviderIntegrationTest extends AbstractNeo4jLockProviderIntegrationTest {
    private static Neo4jTestUtils testUtils;

    @Container
    private static final Neo4jContainer container =
            new Neo4jContainer(DockerImageName.parse("neo4j").withTag("5.22.0")).withoutAuthentication();

    @BeforeAll
    static void startDb() {
        testUtils = new Neo4jTestUtils(GraphDatabase.driver(container.getBoltUrl(), AuthTokens.none()));
    }

    @Override
    protected Neo4jTestUtils getNeo4jTestUtils() {
        return testUtils;
    }
}
