package net.javacrumbs.shedlock.provider.r2dbc;

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
                return new DefaultR2dbcAdapter((index, name) -> "@" + name, R2dbcAdapter::toLocalDate);
            case MYSQL_NAME:
                return new DefaultR2dbcAdapter((index, name) -> "?", R2dbcAdapter::toInstant);
            case MARIA_NAME:
                return new DefaultR2dbcAdapter((index, name) -> "?", R2dbcAdapter::toLocalDate);
            case ORACLE_NAME:
                return new DefaultR2dbcAdapter((index, name) -> ":" + name, R2dbcAdapter::toLocalDate);
            default:
                return new DefaultR2dbcAdapter((index, name) -> "$" + index, R2dbcAdapter::toInstant);
        }
    }

    private static Instant toInstant(Instant date) {
        return date;
    }

    private static LocalDateTime toLocalDate(Instant date) {
        return LocalDateTime.ofInstant(date, ZoneId.systemDefault());
    }

    protected abstract String toParameter(int index, String name);

    protected abstract Object toCompatibleDate(Instant date);

    private static class DefaultR2dbcAdapter extends R2dbcAdapter {
        private final ParameterResolver parameterResolver;
        private final Function<Instant, Object> dateConverter;

        private DefaultR2dbcAdapter(@NonNull ParameterResolver parameterResolver, @NonNull Function<Instant, Object> dateConverter) {
            this.parameterResolver = parameterResolver;
            this.dateConverter = dateConverter;
        }

        @Override
        protected String toParameter(int index, String name) {
            return parameterResolver.resolve(index, name);
        }

        @Override
        protected Object toCompatibleDate(Instant date) {
            return dateConverter.apply(date);
        }
    }

    private interface ParameterResolver {
        String resolve(int index, String name);
    }
}
