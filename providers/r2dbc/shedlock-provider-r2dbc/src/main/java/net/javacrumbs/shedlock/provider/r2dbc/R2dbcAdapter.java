package net.javacrumbs.shedlock.provider.r2dbc;

import io.r2dbc.spi.Statement;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Function;

abstract class R2dbcAdapter {
    private static final String MSSQL_NAME = "Microsoft SQL Server";
    private static final String MYSQL_NAME = "MySQL";
    private static final String MARIA_NAME = "MariaDB";
    private static final String ORACLE_NAME = "Oracle Database";

    static R2dbcAdapter create(@NonNull String driver) {
        switch (driver) {
            case MSSQL_NAME:
                return new DefaultR2dbcAdapter(
                    (index, name) -> "@" + name,
                    R2dbcAdapter::toLocalDate,
                    R2dbcAdapter::bindByName
                );
            case MYSQL_NAME:
            case MARIA_NAME:
                return new DefaultR2dbcAdapter(
                    (index, name) -> "?",
                    R2dbcAdapter::toLocalDate,
                    R2dbcAdapter::bindByIndex
                );
            case ORACLE_NAME:
                return new DefaultR2dbcAdapter(
                    (index, name) -> ":" + name,
                    R2dbcAdapter::toLocalDate,
                    R2dbcAdapter::bindByName
                );
            default:
                return new DefaultR2dbcAdapter(
                    (index, name) -> "$" + index,
                    R2dbcAdapter::toInstant,
                    R2dbcAdapter::bindByIndex
                );
        }
    }

    private static Instant toInstant(Instant date) {
        return date;
    }

    private static LocalDateTime toLocalDate(Instant date) {
        return LocalDateTime.ofInstant(date, ZoneId.systemDefault());
    }

    private static void bindByName(Statement statement, int index, String name, Object value) {
        statement.bind(name, value);
    }
    private static void bindByIndex(Statement statement, int index, String name, Object value) {
        statement.bind(index, value);
    }


    protected abstract String toParameter(int index, String name);

    public abstract void bind(Statement statement, int index, String name, Object value);

    private static class DefaultR2dbcAdapter extends R2dbcAdapter {
        private final ParameterResolver parameterResolver;
        private final Function<Instant, Object> dateConverter;
        private final ValueBinder binder;

        private DefaultR2dbcAdapter(
            @NonNull ParameterResolver parameterResolver,
            @NonNull Function<Instant, Object> dateConverter,
            @NonNull ValueBinder binder
        ) {
            this.parameterResolver = parameterResolver;
            this.dateConverter = dateConverter;
            this.binder = binder;
        }

        @Override
        protected String toParameter(int index, String name) {
            return parameterResolver.resolve(index, name);
        }

        @Override
        public void bind(Statement statement, int index, String name, Object value) {
            binder.bind(statement, index, name, normalizeValue(value));
        }

        private Object normalizeValue(Object value) {
            if (value instanceof Instant) {
                return dateConverter.apply((Instant) value);
            } else {
                return value;
            }
        }
    }

    @FunctionalInterface
    private interface ParameterResolver {
        String resolve(int index, String name);
    }

    @FunctionalInterface
    private interface ValueBinder {
        void bind(Statement statement, int index, String name, Object value);
    }
}
