package com.testproject.calendar.booking;

import com.testproject.calendar.generated.model.BookingDetails;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** RFC 5545 snapshot, deliberately excluding guest data and scheduling/invitation metadata. */
public final class CalendarFile {
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    private CalendarFile() { }

    public static String render(BookingDetails booking, Instant now, String resultUrl) {
        var file = new StringBuilder();
        for (String line : new String[] {
                "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Calendar//Booking snapshot//EN", "CALSCALE:GREGORIAN",
                "BEGIN:VEVENT", "UID:" + booking.getId() + "@calendar-booking",
                "DTSTAMP:" + UTC.format(now),
                "DTSTART:" + UTC.format(booking.getStartsAt().toInstant()),
                "DTEND:" + UTC.format(booking.getEndsAt().toInstant()),
                "SUMMARY:" + text(booking.getTitle()),
                "DESCRIPTION:" + text(booking.getDescription() + "\nВладелец: " + booking.getOwnerName()),
                "STATUS:" + (booking.getStatus() == BookingDetails.StatusEnum.PENDING ? "TENTATIVE" : "CONFIRMED"),
                "URL:" + resultUrl, "END:VEVENT", "END:VCALENDAR" }) {
            int octets = 0;
            for (int code : line.codePoints().toArray()) {
                String character = new String(Character.toChars(code));
                int size = character.getBytes(StandardCharsets.UTF_8).length;
                if (octets + size > 75) {
                    file.append("\r\n ");
                    octets = 1; // The continuation space counts toward the 75-octet limit.
                }
                file.append(character);
                octets += size;
            }
            file.append("\r\n");
        }
        return file.toString();
    }

    private static String text(String value) {
        return value.replace("\\", "\\\\").replace("\r\n", "\n").replace('\r', '\n')
                .replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;");
    }
}
