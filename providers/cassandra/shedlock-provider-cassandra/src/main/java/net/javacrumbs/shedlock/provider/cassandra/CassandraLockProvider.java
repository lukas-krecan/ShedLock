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
package net.javacrumbs.shedlock.provider.cassandra;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import static java.util.Objects.requireNonNull;

/**
 * Cassandra Lock Provider needs a keyspace and uses a lock table <br>
 * Example creating keyspace and table
 *
 * <pre>
 * CREATE KEYSPACE shedlock with replication={'class':'SimpleStrategy', 'replication_factor':1} and durable_writes=true;
 * CREATE TABLE shedlock.lock (name text PRIMARY KEY, lockUntil timestamp, lockedAt timestamp, lockedBy text);
 * </pre>
 */
public class CassandraLockProvider extends StorageBasedLockProvider {
    static final String DEFAULT_TABLE = "lock";

    public CassandraLockProvider(@NonNull CqlSession cqlSession) {
        this(cqlSession, DEFAULT_TABLE, ConsistencyLevel.QUORUM);
    }

    public CassandraLockProvider(@NonNull CqlSession cqlSession, @NonNull String table, @NonNull ConsistencyLevel consistencyLevel) {
        this(Configuration.builder().withCqlSession(cqlSession).withTableName(table).withConsistencyLevel(consistencyLevel).build());
    }

    public CassandraLockProvider(@NonNull Configuration configuration) {
        super(new CassandraStorageAccessor(configuration));
    }

    /**
     * Convenience class to specify configuration
     */
    public static final class Configuration {
        private final String table;
        private final ColumnNames columnNames;
        private final CqlSession cqlSession;
        private final ConsistencyLevel consistencyLevel;

        Configuration(
            @NonNull CqlSession cqlSession,
            @NonNull String table,
            @NonNull ColumnNames columnNames,
            @NonNull ConsistencyLevel consistencyLevel) {
            this.table = requireNonNull(table, "table can not be null");
            this.columnNames = requireNonNull(columnNames, "columnNames can not be null");
            this.cqlSession = requireNonNull(cqlSession, "cqlSession can not be null");
            this.consistencyLevel = requireNonNull(consistencyLevel, "consistencyLevel column can not be null");
        }

        public ColumnNames getColumnNames() {
            return columnNames;
        }

        public String getTable() {
            return table;
        }

        public CqlSession getCqlSession() {
            return cqlSession;
        }

        public ConsistencyLevel getConsistencyLevel() {
            return consistencyLevel;
        }

        public static Configuration.Builder builder() {
            return new Configuration.Builder();
        }

        /**
         * Convenience builder class to build Configuration
         */
        public static final class Builder {
            private String table;
            private ColumnNames columnNames = new ColumnNames("name", "lockUntil", "lockedAt", "lockedBy");
            private CqlSession cqlSession;
            private ConsistencyLevel consistencyLevel;

            public Builder withTableName(@NonNull String table) {
                this.table = table;
                return this;
            }

            public Builder withColumnNames(ColumnNames columnNames) {
                this.columnNames = columnNames;
                return this;
            }

            public Builder withCqlSession(@NonNull CqlSession cqlSession) {
                this.cqlSession = cqlSession;
                return this;
            }

            public Builder withConsistencyLevel(@NonNull ConsistencyLevel consistencyLevel) {
                this.consistencyLevel = consistencyLevel;
                return this;
            }

            public CassandraLockProvider.Configuration build() {
                return new CassandraLockProvider.Configuration(cqlSession, table, columnNames, consistencyLevel);
            }
        }
    }

    /**
     * Convenience class to specify column names
     */
    public static final class ColumnNames {
        private final String lockName;
        private final String lockUntil;
        private final String lockedAt;
        private final String lockedBy;

        /**
         * Each column names are optional and if not specified the default column name would be considered.
         */
        public ColumnNames(String lockName, String lockUntil, String lockedAt, String lockedBy) {
            this.lockName = requireNonNull(lockName, "'lockName' column name can not be null");
            this.lockUntil = requireNonNull(lockUntil,"'lockUntil' column name can not be null");
            this.lockedAt = requireNonNull(lockedAt,"'lockedAt' column name can not be null");
            this.lockedBy = requireNonNull(lockedBy,"'lockedBy' column name can not be null");
        }

        public String getLockName() {
            return lockName;
        }

        public String getLockUntil() {
            return lockUntil;
        }

        public String getLockedAt() {
            return lockedAt;
        }

        public String getLockedBy() {
            return lockedBy;
        }
    }
}
