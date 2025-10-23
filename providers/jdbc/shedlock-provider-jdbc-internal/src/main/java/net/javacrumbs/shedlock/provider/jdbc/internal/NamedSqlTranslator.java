package net.javacrumbs.shedlock.provider.jdbc.internal;

import static net.javacrumbs.shedlock.provider.sql.internal.CalendarUtils.toCalendar;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

class NamedSqlTranslator {

    private static final Pattern namedParameterPattern = Pattern.compile(":[a-zA-Z]+");

    public static SqlStatement translate(String namedSql, Map<String, Object> namedParameters) {
        List<Object> parameters = new ArrayList<>();

        String sql = namedParameterPattern.matcher(namedSql).replaceAll(result -> {
            String key = result.group().substring(1);
            if (!namedParameters.containsKey(key)) {
                throw new IllegalStateException("Parameter " + key + " not found");
            }
            Object value = namedParameters.get(key);
            if (value instanceof ZonedDateTime dateTime) {
                parameters.add(toCalendar(dateTime));
            } else {
                parameters.add(value);
            }
            return "?";
        });

        return new SqlStatement(sql, Collections.unmodifiableList(parameters));
    }

    record SqlStatement(String sql, List<Object> parameters) {}
}
