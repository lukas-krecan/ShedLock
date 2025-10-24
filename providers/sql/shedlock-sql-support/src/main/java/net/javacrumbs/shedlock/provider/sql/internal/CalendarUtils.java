package net.javacrumbs.shedlock.provider.sql.internal;

import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class CalendarUtils {
    private CalendarUtils() {}

    public static Calendar toCalendar(ZonedDateTime dateTime) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getTimeZone(dateTime.getZone()));
        calendar.setTime(Date.from(dateTime.toInstant()));
        return calendar;
    }
}
