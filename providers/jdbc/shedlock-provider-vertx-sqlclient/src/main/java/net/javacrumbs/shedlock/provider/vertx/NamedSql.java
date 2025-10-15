package net.javacrumbs.shedlock.provider.vertx;

import static java.util.stream.Collectors.toUnmodifiableMap;

import java.util.Calendar;
import java.util.Map;
import java.util.regex.Pattern;

class NamedSql {
    private static final Pattern NAMED_PARAMETER_PATTERN = Pattern.compile(":[a-zA-Z]+");

    static Statement translate(String namedSql, Map<String, Object> namedParameters) {
        String sql = NAMED_PARAMETER_PATTERN
                .matcher(namedSql)
                .replaceAll(result -> "#{" + result.group().substring(1) + "}");
        return new Statement(
                sql,
                namedParameters.entrySet().stream()
                        .map(entry -> Map.entry(entry.getKey(), translate(entry.getValue())))
                        .collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private static Object translate(Object value) {
        if (value instanceof Calendar cal) {
            return cal.toInstant().atZone(cal.getTimeZone().toZoneId()).toLocalDateTime();
        } else {
            return value;
        }
    }

    record Statement(String sql, Map<String, Object> parameters) {}
}
