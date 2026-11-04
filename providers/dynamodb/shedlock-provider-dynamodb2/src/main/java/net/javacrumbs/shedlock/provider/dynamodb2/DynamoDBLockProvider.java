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
package net.javacrumbs.shedlock.provider.dynamodb2;

import static net.javacrumbs.shedlock.core.ClockProvider.now;

import java.util.Optional;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.Utils;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Distributed lock using DynamoDB. Depends on
 * <code>software.amazon.awssdk:dynamodb</code>.
 *
 * <p>
 * It uses a table with the following structure:
 *
 * <pre>
 * {
 *    "_id" : "lock name",
 *    "lockUntil" : ISODate("2017-01-07T16:52:04.071Z"),
 *    "lockedAt" : ISODate("2017-01-07T16:52:03.932Z"),
 *    "lockedBy" : "host name"
 * }
 * </pre>
 *
 * <code>lockedAt</code> and <code>lockedBy</code> are just for troubleshooting
 * and are not read by the code.
 *
 * <ol>
 * <li>Attempts to insert a new lock record.
 * <li>We will try to update lock record using
 * <code>filter _id == :name AND lock_until &lt;= :now
 *       </code>.
 * <li>If the update succeeded, we have the lock. If the update failed
 * (condition check exception) somebody else holds the lock.
 * <li>When unlocking, <code>lock_until</code> is set to <i>now</i> or
 * <i>lockAtLeastUntil</i> whichever is later.
 * </ol>
 */
public class DynamoDBLockProvider implements LockProvider {
    static final String LOCK_UNTIL = "lockUntil";
    static final String LOCKED_AT = "lockedAt";
    static final String LOCKED_BY = "lockedBy";
    static final String ID = "_id";
    static final String SORT = "_SortKey";

    private final String hostname;
    private final DynamoDBLockRepository repository;

    /**
     * Uses DynamoDB to coordinate locks
     *
     * @param dynamoDbClient v2 of DynamoDB client
     * @param tableName the lock table name
     */
    public DynamoDBLockProvider(DynamoDbClient dynamoDbClient, String tableName) {
        this(dynamoDbClient, tableName, ID);
    }

    /**
     * Uses DynamoDB to coordinate locks
     *
     * @param dynamoDbClient   v2 of DynamoDB client
     * @param tableName        the lock table name
     * @param partitionKeyName the partitionKey name of table
     */
    public DynamoDBLockProvider(DynamoDbClient dynamoDbClient, String tableName, String partitionKeyName) {
        this(dynamoDbClient, tableName, partitionKeyName, null);
    }

    /**
     * Uses DynamoDB to coordinate locks
     *
     * @param dynamoDbClient   v2 of DynamoDB client
     * @param tableName        the lock table name
     * @param partitionKeyName the partitionKey name of table
     * @param sortKeyName      the sortKey name of table
     */
    public DynamoDBLockProvider(
            DynamoDbClient dynamoDbClient, String tableName, String partitionKeyName, @Nullable String sortKeyName) {
        this(new DynamoDBClientLockRepository(dynamoDbClient, tableName, partitionKeyName, sortKeyName));
    }

    /**
     * Uses DynamoDB to coordinate locks through an enhanced-client table. Requires
     * <code>software.amazon.awssdk:dynamodb-enhanced</code>.
     *
     * @param table lock table, see {@link DynamoDBLockItem}
     */
    public DynamoDBLockProvider(DynamoDbTable<DynamoDBLockItem> table) {
        this(new DynamoDBTableLockRepository(table));
    }

    private DynamoDBLockProvider(DynamoDBLockRepository repository) {
        this.repository = repository;
        this.hostname = Utils.getHostname();
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        try {
            if (repository.obtainLock(
                    lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil(), now(), hostname)) {
                return Optional.of(new DynamoDBLock(repository, lockConfiguration));
            }
            return Optional.empty();
        } catch (Exception e) {
            throw new LockException("Error on lock", e);
        }
    }

    private static final class DynamoDBLock extends AbstractSimpleLock {
        private final DynamoDBLockRepository repository;

        private DynamoDBLock(DynamoDBLockRepository repository, LockConfiguration lockConfiguration) {
            super(lockConfiguration);
            this.repository = repository;
        }

        @Override
        public void doUnlock() {
            // Set lockUntil to now or lockAtLeastUntil whichever is later
            repository.releaseLock(lockConfiguration.getName(), lockConfiguration.getUnlockTime());
        }
    }
}
