package net.javacrumbs.shedlock.cdi.internal;

import java.time.Duration;
import java.time.format.DateTimeParseException;

class Utils {
    static Duration parseDuration(String value) {
        value = value.trim();
        if (value.isEmpty()) {
            return null;
        }

        try {
            return Duration.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
