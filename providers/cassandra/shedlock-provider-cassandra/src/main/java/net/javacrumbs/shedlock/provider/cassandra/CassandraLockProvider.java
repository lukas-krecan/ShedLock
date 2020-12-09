package net.javacrumbs.shedlock.provider.cassandra;

import static java.util.Objects.requireNonNull;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;

import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.support.annotation.NonNull;

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
		super(new CassandraStorageAccessor(Configuration.builder().withCqlSession(cqlSession).withTableName(DEFAULT_TABLE).withConsistencyLevel(ConsistencyLevel.QUORUM).build()));
	}

	public CassandraLockProvider(@NonNull CqlSession cqlSession, @NonNull String table,
			@NonNull ConsistencyLevel consistencyLevel) {
		super(new CassandraStorageAccessor(Configuration.builder().withCqlSession(cqlSession).withTableName(table).withConsistencyLevel(consistencyLevel).build()));
	}

	public CassandraLockProvider(@NonNull Configuration configuration) {
		super(new CassandraStorageAccessor(configuration));
	}

	/**
	 * @author Debajit Kumar Phukan
	 * @description Convenience class to specify configuration 
	 *
	 */
	public static final class Configuration {
		private final String table;
		private ColumnNames columnNames;
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

		public void setColumnNames(ColumnNames columnNames) {
			this.columnNames = columnNames;
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
		 * @author Debajit Kumar Phukan
		 * @description Convenience builder class to build Configuration 
		 *
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
	            if (columnNames != null) {
	            	this.columnNames = columnNames;	
	            }
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
	 * @author Debajit Kumar Phukan
	 * @description Convenience class to specify column names
	 * 
	 */
	public static final class ColumnNames {
		private static final String DEFAULT_LOCK_NAME = "name";
	    private static final String DEFAULT_LOCK_UNTIL = "lockUntil";
	    private static final String DEFAULT_LOCKED_AT = "lockedAt";
	    private static final String DEFAULT_LOCKED_BY = "lockedBy";
	    private final String lockName;
	    private final String lockUntil;
	    private final String lockedAt;
	    private final String lockedBy;

	    /**
		 * @description Each column names are optional and if not specified the default column name would be considered.
		 * 
		 */
        public ColumnNames(String lockNameColumn, String lockUntilColumn, String lockedAtColumn, String lockedByColumn) {
        	this.lockName = lockNameColumn != null ? lockNameColumn : DEFAULT_LOCK_NAME;
	        this.lockUntil = lockUntilColumn != null ? lockUntilColumn : DEFAULT_LOCK_UNTIL;
	        this.lockedAt = lockedAtColumn != null ? lockedAtColumn : DEFAULT_LOCKED_AT;
	        this.lockedBy = lockedByColumn != null ? lockedByColumn : DEFAULT_LOCKED_BY;
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