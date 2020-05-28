package net.javacrumbs.shedlock.core;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Enables to change Clock for all ShedLock classes
 */
public class ClockProvider {
    private static Clock clock = Clock.systemUTC();

    public static void setClock(Clock clock) {
        ClockProvider.clock = clock;
    }

    public static Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
