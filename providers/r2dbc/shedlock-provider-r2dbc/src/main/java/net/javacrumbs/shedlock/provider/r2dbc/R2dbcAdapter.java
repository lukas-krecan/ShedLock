package net.javacrumbs.shedlock.provider.r2dbc;

import io.r2dbc.spi.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.function.Function;
import net.javacrumbs.shedlock.provider.sql.DatabaseProduct;

abstract class R2dbcAdapter {

    static R2dbcAdapter create(DatabaseProduct databaseProduct) {
        return switch (databaseProduct) {
            case SQL_SERVER ->
                new DefaultR2dbcAdapter(
                        (index, name) -> "@" + name, R2dbcAdapter::toLocalDateTime, R2dbcAdapter::bindByName);
            case MY_SQL, MARIA_DB ->
                new DefaultR2dbcAdapter((index, name) -> "?", R2dbcAdapter::toLocalDateTime, R2dbcAdapter::bindByIndex);
            case ORACLE ->
                new DefaultR2dbcAdapter(
                        (index, name) -> ":" + name, R2dbcAdapter::toLocalDateTime, R2dbcAdapter::bindByName);
            default ->
                new DefaultR2dbcAdapter(
                        (index, name) -> "$" + index, R2dbcAdapter::toInstant, R2dbcAdapter::bindByIndex);
        };
    }

    private static Instant toInstant(Calendar date) {
        return date.toInstant();
    }

    private static LocalDateTime toLocalDateTime(GregorianCalendar date) {
        return date.toZonedDateTime().toLocalDateTime();
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
        private final Function<GregorianCalendar, Object> dateConverter;
        private final ValueBinder binder;

        private DefaultR2dbcAdapter(
                ParameterResolver parameterResolver,
                Function<GregorianCalendar, Object> dateConverter,
                ValueBinder binder) {
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
            if (value instanceof GregorianCalendar calendar) {
                return dateConverter.apply(calendar);
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
