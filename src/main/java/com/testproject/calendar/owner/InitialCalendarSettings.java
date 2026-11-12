package com.testproject.calendar.owner;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("calendar.initial")
public record InitialCalendarSettings(String name, String username, String timeZone, Boolean bookingEnabled,
        List<DayOfWeek> workingDays, String workStart, String workEnd) {

    public InitialCalendarSettings {
        name = printable(name, "name").replaceAll("\\p{Zs}+", " ").strip();
        int length = name.codePointCount(0, name.length());
        require(length >= 1 && length <= 100, "name", "expected 1–100 printable characters");
        username = printable(username, "username").strip().toLowerCase(Locale.ROOT);
        require(username.matches("[a-z0-9][a-z0-9-]{1,28}[a-z0-9]"), "username",
                "expected 3–30 letters, digits or hyphens, starting and ending with a letter or digit");
        require(timeZone != null && ZoneId.getAvailableZoneIds().contains(timeZone),
                "time-zone", "expected an IANA time zone");
        require(bookingEnabled != null, "booking-enabled", "expected true or false");
        require(workingDays != null, "working-days", "expected a list of weekdays");
        workingDays = List.copyOf(workingDays);
        int start = minute(workStart, false, "work-start");
        int end = minute(workEnd, true, "work-end");
        require(start < end, "work-end", "must be strictly after calendar.initial.work-start");
    }

    private static String printable(String value, String property) {
        require(value != null, property, "value is required");
        require(value.codePoints().noneMatch(code -> Character.isISOControl(code)
                || Character.getType(code) == Character.FORMAT
                || Character.getType(code) == Character.SURROGATE
                || Character.getType(code) == Character.UNASSIGNED
                || Character.getType(code) == Character.LINE_SEPARATOR
                || Character.getType(code) == Character.PARAGRAPH_SEPARATOR),
                property, "control and invisible characters are not allowed");
        return value;
    }

    private static int minute(String value, boolean allowMidnight, String property) {
        if (allowMidnight && "24:00".equals(value)) {
            return 24 * 60;
        }
        require(value != null && value.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]"),
                property, "expected HH:mm within a day; 24:00 is allowed only as the end");
        return Integer.parseInt(value.substring(0, 2)) * 60 + Integer.parseInt(value.substring(3));
    }

    private static void require(boolean condition, String property, String message) {
        if (!condition) {
            throw new IllegalArgumentException("calendar.initial." + property + ": " + message);
        }
    }
}
