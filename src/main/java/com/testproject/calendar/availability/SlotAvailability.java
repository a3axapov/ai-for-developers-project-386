package com.testproject.calendar.availability;

import com.testproject.calendar.generated.model.MeetingType;
import com.testproject.calendar.generated.model.PublicSlots;
import com.testproject.calendar.generated.model.Slot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SlotAvailability {
    private final Clock clock;

    public SlotAvailability(Clock clock) {
        this.clock = clock;
    }

    public PublicSlots slots(MeetingType type, String ownerTimeZone, String conditionsToken) {
        var ownerZone = ZoneId.of(ownerTimeZone);
        var tomorrow = LocalDate.now(clock.withZone(ownerZone)).plusDays(1);
        var start = tomorrow.atStartOfDay(ownerZone);
        var end = tomorrow.plusDays(14).atStartOfDay(ownerZone);
        return new PublicSlots(start.toOffsetDateTime(), end.toOffsetDateTime(), ownerTimeZone,
                type.getTimeZone(), conditionsToken, available(type, start.toInstant(), end.toInstant()));
    }

    public List<Slot> available(MeetingType type, Instant start, Instant end) {
        var zone = ZoneId.of(type.getTimeZone());
        var first = start.atZone(zone).toLocalDate();
        var last = end.atZone(zone).toLocalDate();
        var ranges = new ArrayList<Range>();
        for (var date = first; !date.isAfter(last); date = date.plusDays(1)) {
            var day = date;
            var intervals = type.getDays().stream().filter(value -> value.getDay().name()
                    .equals(day.getDayOfWeek().name())).flatMap(value -> value.getIntervals().stream()).toList();
            for (var interval : intervals) {
                var from = date.atTime(LocalTime.parse(interval.getStart()));
                var to = interval.getEnd().equals("24:00") ? date.plusDays(1).atStartOfDay()
                        : date.atTime(LocalTime.parse(interval.getEnd()));
                ranges.addAll(actualRanges(from, to, zone));
            }
        }
        var continuous = new ArrayList<Range>();
        for (var range : ranges.stream().sorted(Comparator.comparing(Range::start)).toList()) {
            if (continuous.isEmpty() || continuous.getLast().end().isBefore(range.start())) {
                continuous.add(range);
            } else {
                var previous = continuous.removeLast();
                continuous.add(new Range(previous.start(), previous.end().isAfter(range.end())
                        ? previous.end() : range.end()));
            }
        }
        var items = new ArrayList<Slot>();
        for (var date = first; !date.isAfter(last); date = date.plusDays(1)) {
            for (int minute = 0; minute < 1440; minute += 15) {
                var local = date.atStartOfDay().plusMinutes(minute);
                for (var offset : zone.getRules().getValidOffsets(local)) {
                    var candidate = local.toInstant(offset);
                    var finish = candidate.plusSeconds(type.getDurationMinutes() * 60L);
                    if (!candidate.isBefore(start) && !finish.isAfter(end)
                            && continuous.stream().anyMatch(range -> !candidate.isBefore(range.start())
                            && !finish.isAfter(range.end()))) {
                        items.add(new Slot(candidate.atZone(zone).toOffsetDateTime(),
                                finish.atZone(zone).toOffsetDateTime()));
                    }
                }
            }
        }
        return items.stream().sorted(Comparator.comparing(slot -> slot.getStartsAt().toInstant())).toList();
    }

    // Intersect wall-clock intervals with each constant-offset segment of the actual timeline.
    // Unlike atZone(), this neither shifts nonexistent boundaries nor bridges repeated sections.
    private List<Range> actualRanges(LocalDateTime from, LocalDateTime to, ZoneId zone) {
        var result = new ArrayList<Range>();
        var cursor = from.toInstant(ZoneOffset.MAX);
        var limit = to.toInstant(ZoneOffset.MIN);
        var rules = zone.getRules();
        while (cursor.isBefore(limit)) {
            var transition = rules.nextTransition(cursor);
            var segmentEnd = transition == null || transition.getInstant().isAfter(limit)
                    ? limit : transition.getInstant();
            var offset = rules.getOffset(cursor);
            var start = from.toInstant(offset).isAfter(cursor) ? from.toInstant(offset) : cursor;
            var end = to.toInstant(offset).isBefore(segmentEnd) ? to.toInstant(offset) : segmentEnd;
            if (start.isBefore(end)) {
                result.add(new Range(start, end));
            }
            cursor = segmentEnd;
        }
        return result;
    }

    private record Range(Instant start, Instant end) { }
}
