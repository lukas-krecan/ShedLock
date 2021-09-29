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
package net.javacrumbs.shedlock.provider.neo4j;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.singletonMap;

public final class Neo4jTestUtils {

    private final Driver driver;

    public Neo4jTestUtils(Driver driver) {
        this.driver = driver;
    }

    public void executeTransactionally(String query) {
        executeTransactionally(query, new HashMap<>(), null);
    }

    public <T> T executeTransactionally(String query, Map<String, Object> parameters, Function<Result, T> resultTransformer) {
        T transformedResult = null;
        try (Session session = driver.session()) {
            Transaction transaction = session.beginTransaction();
            Result result = transaction.run(query, parameters);
            if (resultTransformer != null) {
                transformedResult = resultTransformer.apply(result);
            }
            transaction.commit();
        }
        return transformedResult;
    }

    public Instant getLockedUntil(String lockName) {
        Map<String, Object> parameters = singletonMap("lockName", lockName);
        return executeTransactionally("MATCH (lock:shedlock) WHERE lock.name = $lockName return lock.lock_until",
            parameters, result -> result.stream()
                .findFirst()
                .map(it -> Instant.parse(it.get("lock.lock_until").asString()))
                .orElse(null));
    }

    public LockInfo getLockInfo(String lockName) {
        Map<String, Object> parameters = singletonMap("lockName", lockName);
        return executeTransactionally("MATCH (lock:shedlock) WHERE lock.name = $lockName RETURN lock.name, lock.lock_until, localdatetime() as db_time ", parameters, result ->
            result.stream()
                .findFirst()
                .map(it -> new LockInfo(
                        it.get("lock.name").asString(),
                        Instant.parse(it.get("lock.lock_until").asString())
                    )
                )
        ).orElse(null);
    }

    public void clean() {
        executeTransactionally("MATCH (lock:shedlock) DELETE lock");
    }

    public Driver getDriver() {
        return driver;
    }

    public static class LockInfo {
        private final String name;
        private final Instant lockUntil;

        LockInfo(String name, Instant lockUntil) {
            this.name = name;
            this.lockUntil = lockUntil;
        }

        public String getName() {
            return name;
        }

        public Instant getLockUntil() {
            return lockUntil;
        }
    }
}
