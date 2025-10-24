package net.javacrumbs.shedlock.provider.sql.internal;

import org.jspecify.annotations.Nullable;

public class ErrorCodeUtils {
    private ErrorCodeUtils() {}

    public static boolean isConstraintViolation(@Nullable String sqlState) {
        // SQL State class '23' indicates constraint violation
        if (sqlState != null) {
            return sqlState.startsWith("23");
        } else {
            return false;
        }
    }
}
