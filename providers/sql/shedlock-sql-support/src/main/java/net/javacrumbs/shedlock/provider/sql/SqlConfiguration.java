package net.javacrumbs.shedlock.provider.sql;

import static java.util.Objects.requireNonNull;

import java.util.TimeZone;
import net.javacrumbs.shedlock.support.Utils;
import org.jspecify.annotations.Nullable;

/**
 * Provider-agnostic configuration contract for SQL statement generation.
 */
public abstract class SqlConfiguration {

    public static final String DEFAULT_TABLE_NAME = "shedlock";

    @Nullable
    private final DatabaseProduct databaseProduct;

    private final String tableName;

    @Nullable
    private final TimeZone timeZone;

    private final ColumnNames columnNames;
    private final String lockedByValue;
    private final boolean useDbTime;

    @Nullable
    private final Integer isolationLevel;

    private final boolean throwUnexpectedException;

    protected SqlConfiguration(
            @Nullable DatabaseProduct databaseProduct,
            boolean dbUpperCase,
            String tableName,
            @Nullable TimeZone timeZone,
            ColumnNames columnNames,
            String lockedByValue,
            boolean useDbTime,
            @Nullable Integer isolationLevel,
            boolean throwUnexpectedException) {
        this.databaseProduct = databaseProduct;
        requireNonNull(tableName, "tableName can not be null");
        this.tableName = dbUpperCase ? tableName.toUpperCase() : tableName;
        this.timeZone = timeZone;
        requireNonNull(columnNames, "columnNames can not be null");
        this.columnNames = dbUpperCase ? columnNames.toUpperCase() : columnNames;
        this.lockedByValue = requireNonNull(lockedByValue, "lockedByValue can not be null");
        this.isolationLevel = isolationLevel;
        if (useDbTime && timeZone != null) {
            throw new IllegalArgumentException("Can not set both useDbTime and timeZone");
        }
        this.useDbTime = useDbTime;
        this.throwUnexpectedException = throwUnexpectedException;
    }

    @Nullable
    public DatabaseProduct getDatabaseProduct() {
        return databaseProduct;
    }

    public String getTableName() {
        return tableName;
    }

    @Nullable
    public TimeZone getTimeZone() {
        return timeZone;
    }

    public ColumnNames getColumnNames() {
        return columnNames;
    }

    public String getLockedByValue() {
        return lockedByValue;
    }

    public boolean getUseDbTime() {
        return useDbTime;
    }

    @Nullable
    public Integer getIsolationLevel() {
        return isolationLevel;
    }

    public boolean isThrowUnexpectedException() {
        return throwUnexpectedException;
    }

    public abstract static class SqlConfigurationBuilder<T extends SqlConfigurationBuilder<T>> {

        @Nullable
        protected DatabaseProduct databaseProduct;

        protected String tableName = DEFAULT_TABLE_NAME;

        protected String lockedByValue = Utils.getHostname();
        protected ColumnNames columnNames = new ColumnNames("name", "lock_until", "locked_at", "locked_by");
        protected boolean dbUpperCase = false;
        protected boolean useDbTime = false;

        @Nullable
        protected Integer isolationLevel;

        protected boolean throwUnexpectedException = false;

        public T withTableName(String tableName) {
            this.tableName = tableName;
            return getThis();
        }

        public T withColumnNames(ColumnNames columnNames) {
            this.columnNames = columnNames;
            return getThis();
        }

        public T withDbUpperCase(final boolean dbUpperCase) {
            this.dbUpperCase = dbUpperCase;
            return getThis();
        }

        /**
         * This is only needed if your database product can't be automatically detected.
         *
         * @param databaseProduct
         *            Database product
         * @return ConfigurationBuilder
         */
        public T withDatabaseProduct(final DatabaseProduct databaseProduct) {
            this.databaseProduct = databaseProduct;
            return getThis();
        }

        /**
         * Value stored in 'locked_by' column. Please use only for debugging purposes.
         */
        public T withLockedByValue(String lockedBy) {
            this.lockedByValue = lockedBy;
            return getThis();
        }

        public T usingDbTime() {
            this.useDbTime = true;
            return getThis();
        }

        /**
         * Sets the isolation level for ShedLock. See {@link java.sql.Connection} for
         * constant definitions. for constant definitions
         */
        public T withIsolationLevel(int isolationLevel) {
            this.isolationLevel = isolationLevel;
            return getThis();
        }

        public T withThrowUnexpectedException(boolean throwUnexpectedException) {
            this.throwUnexpectedException = throwUnexpectedException;
            return getThis();
        }

        @SuppressWarnings("unchecked")
        protected T getThis() {
            return (T) this;
        }
    }

    public static class ColumnNames {
        private final String name;
        private final String lockUntil;
        private final String lockedAt;
        private final String lockedBy;

        public ColumnNames(String name, String lockUntil, String lockedAt, String lockedBy) {
            this.name = requireNonNull(name, "'name' column name can not be null");
            this.lockUntil = requireNonNull(lockUntil, "'lockUntil' column name can not be null");
            this.lockedAt = requireNonNull(lockedAt, "'lockedAt' column name can not be null");
            this.lockedBy = requireNonNull(lockedBy, "'lockedBy' column name can not be null");
        }

        public String getName() {
            return name;
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

        ColumnNames toUpperCase() {
            return new ColumnNames(
                    name.toUpperCase(), lockUntil.toUpperCase(), lockedAt.toUpperCase(), lockedBy.toUpperCase());
        }
    }
}
