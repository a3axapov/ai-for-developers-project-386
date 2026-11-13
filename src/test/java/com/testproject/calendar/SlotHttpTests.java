package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.testproject.calendar.generated.model.WorkingDay;
import com.testproject.calendar.generated.model.WorkingInterval;
import com.testproject.calendar.owner.OwnerState;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SlotHttpTests {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void archivedFixtureDoesNotExposeSlotsOrConditions() throws Exception {
        try (var app = start(new TestClock("2026-11-01T00:00:00Z"))) {
            create(app, 30);
            app.getBean(OwnerState.class).meetingType("Consult").setArchived(true);
            var response = send(app, "GET", "/api/public/owner/meeting-types/Consult/slots", null);
            assertEquals(409, response.statusCode(), response.body());
            assertEquals(json.readTree("""
                    {"code":"TYPE_ARCHIVED","message":"Запись на этот тип встречи прекращена"}
                    """), json.readTree(response.body()));
        }
    }

    @Test
    void clockCrossingOwnerMidnightRecomputesWindowWithoutAnyTimeEndpoint() throws Exception {
        var clock = new TestClock("2026-11-01T20:59:59Z");
        try (var app = start(clock, "--calendar.initial.working-days=")) {
            create(app, 1);
            assertEquals(Instant.parse("2026-11-01T21:00:00Z"), instant(slots(app), "windowStart"));
            clock.set("2026-11-01T21:00:00Z");
            assertEquals(Instant.parse("2026-11-02T21:00:00Z"), instant(slots(app), "windowStart"));
            assertEquals(Instant.parse("2026-11-16T21:00:00Z"), instant(slots(app), "windowEnd"));
            clock.set("2026-11-02T20:59:59Z");
            assertEquals(Instant.parse("2026-11-02T21:00:00Z"), instant(slots(app), "windowStart"));
        }
    }

    @ParameterizedTest
    @CsvSource({
        "2026-03-28T12:00:00Z,02:15,03:30,30,2026-03-29T03:00:00+02:00,2026-03-29T03:30:00+02:00",
        "2026-03-28T12:00:00Z,01:00,04:00,60,2026-03-29T01:00:00+01:00,2026-03-29T03:00:00+02:00",
        "2026-10-24T12:00:00Z,01:00,04:00,120,2026-10-25T01:00:00+02:00,2026-10-25T02:00:00+01:00"
    })
    void durationIsElapsedTimeAndPartlyNonexistentHoursUseOnlyExistingParts(
            String now, String from, String to, int duration, String firstStart, String firstEnd) throws Exception {
        try (var app = start(new TestClock(now), "--calendar.initial.time-zone=Europe/Berlin",
                "--calendar.initial.working-days=SUNDAY", "--calendar.initial.work-start=" + from,
                "--calendar.initial.work-end=" + to)) {
            create(app, duration);
            var page = slots(app);
            var first = page.get("items").get(0);
            assertEquals(firstStart, first.get("startsAt").asString());
            assertEquals(firstEnd, first.get("endsAt").asString());
            for (var item : page.get("items")) {
                assertEquals(duration * 60L, java.time.Duration.between(
                        instant(item, "startsAt"), instant(item, "endsAt")).getSeconds());
            }
            // Fourteen calendar days include the transition: not a fixed 336-hour duration.
            assertEquals(now.startsWith("2026-03") ? 335 : 337,
                    java.time.Duration.between(instant(page, "windowStart"), instant(page, "windowEnd")).toHours());
        }
    }

    @Test
    void repeatedWorkingSectionsDoNotAccidentallyOpenTheGapBetweenThem() throws Exception {
        try (var app = start(new TestClock("2026-10-24T12:00:00Z"),
                "--calendar.initial.time-zone=Europe/Berlin", "--calendar.initial.working-days=SUNDAY",
                "--calendar.initial.work-start=02:15", "--calendar.initial.work-end=02:45")) {
            create(app, 45);
            assertEquals(0, slots(app).get("items").size());
        }
    }

    @Test
    void dailyBreakCannotBeCrossedButTouchingAndOverlappingIntervalsAreContinuous() throws Exception {
        try (var app = start(new TestClock("2026-11-01T00:00:00Z"))) {
            create(app, 60);
            // Test-only initial fixture: no availability-editing endpoint is added in this slice.
            var type = app.getBean(OwnerState.class).meetingType("Consult");
            type.setDays(java.util.List.of(new WorkingDay(WorkingDay.DayEnum.MONDAY, java.util.List.of(
                    new WorkingInterval("09:00", "09:30"), new WorkingInterval("09:30", "10:00"),
                    new WorkingInterval("09:15", "09:45"), new WorkingInterval("10:01", "11:01")))));
            var items = slots(app).get("items");
            assertEquals(2, items.size());
            assertEquals("2026-11-02T09:00:00+03:00", items.get(0).get("startsAt").asString());
            assertEquals("2026-11-09T09:00:00+03:00", items.get(1).get("startsAt").asString());
        }
    }

    @Test
    void typeZoneDefinesGridWhileOwnerZoneDefinesBothEdgesAndGuestZoneIsIgnored() throws Exception {
        try (var app = start(new TestClock("2026-11-01T20:59:59Z"),
                "--calendar.initial.working-days=MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY",
                "--calendar.initial.work-start=00:00", "--calendar.initial.work-end=24:00")) {
            create(app, 1);
            app.getBean(OwnerState.class).meetingType("Consult")
                    .setTimeZone("Asia/Kathmandu");
            var page = slots(app);
            assertEquals("Europe/Moscow", page.get("ownerTimeZone").asString());
            assertEquals("Asia/Kathmandu", page.get("meetingTypeTimeZone").asString());
            var items = page.get("items");
            assertEquals(1344, items.size());
            assertEquals("2026-11-02T02:45:00+05:45", items.get(0).get("startsAt").asString());
            assertEquals("2026-11-16T02:30:00+05:45", items.get(1343).get("startsAt").asString());
            assertEquals(page, json.readTree(send(app, "GET",
                    "/api/public/owner/meeting-types/Consult/slots?timeZone=America/Los_Angeles", null).body()));
        }
    }

    @Test
    void slotReadRejectsDisabledBookingAfterResolvingBothAddressParts() throws Exception {
        try (var app = start(new TestClock("2026-11-01T00:00:00Z"),
                "--calendar.initial.booking-enabled=false")) {
            create(app, 30);
            var disabled = send(app, "GET", "/api/public/owner/meeting-types/Consult/slots", null);
            assertEquals(409, disabled.statusCode(), disabled.body());
            assertEquals("BOOKING_DISABLED", json.readTree(disabled.body()).get("code").asString());
            assertEquals("no-store", disabled.headers().firstValue("Cache-Control").orElseThrow());
            for (var path : java.util.List.of("/api/public/unknown/meeting-types/Consult/slots",
                    "/api/public/owner/meeting-types/unknown/slots")) {
                var missing = send(app, "GET", path, null);
                assertEquals(404, missing.statusCode(), missing.body());
                assertEquals("PAGE_NOT_FOUND", json.readTree(missing.body()).get("code").asString());
                assertEquals("Страница не найдена", json.readTree(missing.body()).get("message").asString());
            }
        }
    }

    @Test
    void autumnOverlapOffersBothActualInstantsWithExplicitOffsetsInTimeOrder() throws Exception {
        try (var app = start(new TestClock("2026-10-24T12:00:00Z"),
                "--calendar.initial.time-zone=Europe/Berlin", "--calendar.initial.working-days=SUNDAY",
                "--calendar.initial.work-start=02:15", "--calendar.initial.work-end=02:45")) {
            create(app, 30);
            var items = slots(app).get("items");
            assertEquals(3, items.size());
            assertEquals("2026-10-25T02:15:00+02:00", items.get(0).get("startsAt").asString());
            assertEquals("2026-10-25T02:45:00+02:00", items.get(0).get("endsAt").asString());
            assertEquals("2026-10-25T02:15:00+01:00", items.get(1).get("startsAt").asString());
            assertEquals("2026-10-25T02:45:00+01:00", items.get(1).get("endsAt").asString());
        }
    }

    @Test
    void springGapDoesNotSilentlyShiftNonexistentWorkingIntervalOrGridStarts() throws Exception {
        try (var app = start(new TestClock("2026-03-28T12:00:00Z"),
                "--calendar.initial.time-zone=Europe/Berlin", "--calendar.initial.working-days=SUNDAY",
                "--calendar.initial.work-start=02:15", "--calendar.initial.work-end=02:45")) {
            create(app, 30);
            var items = slots(app).get("items");
            assertEquals(1, items.size());
            assertEquals("2026-04-05T02:15:00+02:00", items.get(0).get("startsAt").asString());
            assertEquals("2026-04-05T02:45:00+02:00", items.get(0).get("endsAt").asString());
        }
    }

    @Test
    void touchingDatesCoverMidnightAndBothWindowEdgesAllowExactEnd() throws Exception {
        try (var app = start(new TestClock("2026-11-01T20:59:59Z"),
                "--calendar.initial.working-days=MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY",
                "--calendar.initial.work-start=00:00", "--calendar.initial.work-end=24:00")) {
            create(app, 480);
            var items = slots(app).get("items");
            assertEquals(1313, items.size());
            assertEquals("2026-11-02T00:00:00+03:00", items.get(0).get("startsAt").asString());
            assertEquals("2026-11-02T23:45:00+03:00", items.get(95).get("startsAt").asString());
            assertEquals("2026-11-03T07:45:00+03:00", items.get(95).get("endsAt").asString());
            assertEquals("2026-11-15T16:00:00+03:00", items.get(1312).get("startsAt").asString());
            assertEquals("2026-11-16T00:00:00+03:00", items.get(1312).get("endsAt").asString());
        }
    }

    @Test
    void gridStaysAtQuarterHoursAndWholeActualDurationFitsWorkingInterval() throws Exception {
        try (var app = start(new TestClock("2026-11-01T00:00:00Z"),
                "--calendar.initial.working-days=MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY",
                "--calendar.initial.work-start=09:10", "--calendar.initial.work-end=10:01")) {
            create(app, 31);
            var slots = slots(app);
            assertEquals(28, slots.get("items").size());
            assertEquals("2026-11-02T09:15:00+03:00", slots.get("items").get(0).get("startsAt").asString());
            assertEquals("2026-11-02T09:46:00+03:00", slots.get("items").get(0).get("endsAt").asString());
            assertEquals("2026-11-02T10:01:00+03:00", slots.get("items").get(1).get("endsAt").asString());
            assertEquals("2026-11-15T09:30:00+03:00", slots.get("items").get(27).get("startsAt").asString());
        }
    }

    @Test
    void returnsWholeFourteenCalendarDayWindowFromTomorrowInOwnerZone() throws Exception {
        var clock = new TestClock("2026-11-01T21:00:00Z"); // Already November 2 in Moscow.
        try (var app = start(clock, "--calendar.initial.working-days=")) {
            create(app, 30);
            var response = send(app, "GET", "/api/public/owner/meeting-types/Consult/slots", null);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var slots = json.readTree(response.body());
            assertEquals(Instant.parse("2026-11-02T21:00:00Z"), instant(slots, "windowStart"));
            assertEquals(Instant.parse("2026-11-16T21:00:00Z"), instant(slots, "windowEnd"));
            assertEquals("Europe/Moscow", slots.get("ownerTimeZone").asString());
            assertEquals("Europe/Moscow", slots.get("meetingTypeTimeZone").asString());
            var type = json.readTree(send(app, "GET", "/api/public/owner/meeting-types/Consult", null).body());
            assertEquals(type.get("meetingType").get("conditionsToken"), slots.get("conditionsToken"));
            assertEquals(json.readTree("[]"), slots.get("items"));
        }
    }

    private ConfigurableApplicationContext start(TestClock clock, String... settings) {
        var args = new java.util.ArrayList<>(java.util.List.of("--server.port=0"));
        if (java.util.Arrays.stream(settings)
                .noneMatch(value -> value.startsWith("--calendar.initial.booking-enabled"))) {
            args.add("--calendar.initial.booking-enabled=true");
        }
        args.addAll(java.util.List.of(settings));
        return new SpringApplicationBuilder(CalendarApplication.class)
                .initializers(context -> context.getBeanFactory().registerSingleton("testClock", clock))
                .run(args.toArray(String[]::new));
    }

    private void create(ConfigurableApplicationContext app, int duration) throws Exception {
        var result = send(app, "POST", "/api/owner/meeting-types", """
                {"id":"Consult","title":"Консультация","description":"Подробное описание нашей встречи",
                 "durationMinutes":%d}
                """.formatted(duration));
        assertEquals(201, result.statusCode(), result.body());
    }

    private HttpResponse<String> send(ConfigurableApplicationContext app, String method, String path, String body)
            throws Exception {
        int port = ((WebServerApplicationContext) app).getWebServer().getPort();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .header("Content-Type", "application/json").header("Idempotency-Key", "seed")
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private Instant instant(JsonNode node, String field) {
        return java.time.OffsetDateTime.parse(node.get(field).asString()).toInstant();
    }

    private JsonNode slots(ConfigurableApplicationContext app) throws Exception {
        var response = send(app, "GET", "/api/public/owner/meeting-types/Consult/slots", null);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    static final class TestClock extends Clock {
        private volatile Instant now;

        TestClock(String now) {
            this.now = Instant.parse(now);
        }

        void set(String value) {
            now = Instant.parse(value);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
