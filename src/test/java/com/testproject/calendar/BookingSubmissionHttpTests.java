package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class BookingSubmissionHttpTests {
    private final JsonMapper json = JsonMapper.builder().build();

    @ParameterizedTest
    @CsvSource({"09:30", "10:00"})
    void concurrentNonoverlappingAndTouchingSubmissionsBothSucceed(String time) throws Exception {
        try (var app = start(new MutableClock())) {
            seed(app, "Manual", true, 30);
            seed(app, "Auto", false, 30);
            var first = prepare(app, "Manual", "2026-11-02T09:00:00+03:00");
            var second = prepare(app, "Auto", "2026-11-02T" + time + ":00+03:00");
            var gate = new java.util.concurrent.CyclicBarrier(2);
            try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var one = pool.submit(() -> {
                    gate.await();
                    return submit(app, first);
                });
                var two = pool.submit(() -> {
                    gate.await();
                    return submit(app, second);
                });
                var results = java.util.List.of(one.get(), two.get());
                for (var response : results) {
                    assertEquals(201, response.statusCode(), response.body());
                    var result = json.readTree(response.body());
                    assertEquals(result.get("booking"), get(app, "/api" + result.get("resultUrl").asString()));
                }
                assertNotEquals(json.readTree(results.get(0).body()).get("booking").get("id"),
                        json.readTree(results.get(1).body()).get("booking").get("id"));
            }
        }
    }

    @Test
    void savedSnapshotAndSuccessSurviveChangedConditionsAndArchivalWhileNewSubmissionsRespectPriority()
            throws Exception {
        try (var app = start(new MutableClock())) {
            seed(app, "Consult", true, 30);
            var success = prepare(app, "Consult", "2026-11-02T09:00:00+03:00");
            var stale = prepare(app, "Consult", "2026-11-02T10:00:00+03:00");
            var archived = prepare(app, "Consult", "2026-11-02T11:00:00+03:00");
            var disabled = prepare(app, "Consult", "2026-11-02T12:00:00+03:00");
            var first = json.readTree(submit(app, success).body());
            // Test-only state fixture, as in SlotHttpTests: editing/archival APIs belong to a later slice.
            var type = app.getBean(com.testproject.calendar.owner.OwnerState.class).meetingType("Consult");
            type.setTitle("Новые условия");
            type.setDescription("Другое подробное описание встречи");
            type.setDurationMinutes(60);
            type.setRequiresApproval(false);
            var staleFailure = submit(app, stale);
            assertError(staleFailure, 409, "CONDITIONS_CHANGED");
            type.setArchived(true);
            assertError(submit(app, archived), 409, "TYPE_ARCHIVED");
            assertEquals(200, send(app, "PUT", "/api/owner/public-booking",
                    "{\"enabled\":false,\"expectedVersion\":1}", null).statusCode());
            assertError(submit(app, disabled), 409, "BOOKING_DISABLED");
            assertEquals(staleFailure.body(), submit(app, stale).body());
            var replay = submit(app, success);
            assertEquals(200, replay.statusCode());
            assertEquals(first, json.readTree(replay.body()));
            assertEquals(first.get("booking"), get(app, "/api" + first.get("resultUrl").asString()));
        }
    }

    @ParameterizedTest
    @CsvSource({
        "1,2026-11-02T00:00:00+03:00,2026-11-02T00:01:00+03:00",
        "480,2026-11-02T23:45:00+03:00,2026-11-03T07:45:00+03:00",
        "480,2026-11-15T16:00:00+03:00,2026-11-16T00:00:00+03:00"
    })
    void acceptsDurationBoundariesContinuousMidnightAndExactWindowEnd(int duration, String start, String end)
            throws Exception {
        try (var app = start(new MutableClock(),
                "--calendar.initial.working-days=MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY",
                "--calendar.initial.work-start=00:00", "--calendar.initial.work-end=24:00")) {
            seed(app, "Consult", false, duration);
            var response = submit(app, prepare(app, "Consult", start));
            assertEquals(201, response.statusCode(), response.body());
            var booking = json.readTree(response.body()).get("booking");
            assertEquals(java.time.OffsetDateTime.parse(end).toInstant(),
                    java.time.OffsetDateTime.parse(booking.get("endsAt").asString()).toInstant());
        }
    }

    @Test
    void submissionRechecksChangedHoursAndDoesNotBridgeBreaks() throws Exception {
        try (var app = start(new MutableClock())) {
            seed(app, "Consult", false, 60);
            var unavailable = prepare(app, "Consult", "2026-11-02T09:00:00+03:00");
            var touching = prepare(app, "Consult", "2026-11-02T10:00:00+03:00");
            app.getBean(com.testproject.calendar.owner.OwnerState.class).meetingType("Consult").setDays(
                    java.util.List.of(new com.testproject.calendar.generated.model.WorkingDay(
                    com.testproject.calendar.generated.model.WorkingDay.DayEnum.MONDAY, java.util.List.of(
                    new com.testproject.calendar.generated.model.WorkingInterval("09:00", "09:30"),
                    new com.testproject.calendar.generated.model.WorkingInterval("09:45", "10:30"),
                    new com.testproject.calendar.generated.model.WorkingInterval("10:30", "11:00")))));
            assertError(submit(app, unavailable), 409, "SLOT_UNAVAILABLE");
            // Hours do not change conditionsToken; touching sections still cover the complete duration.
            assertEquals(201, submit(app, touching).statusCode());
        }
    }

    @Test
    void deniesIdsAndWrongSecretsAndRejectsMutationWithoutConsumingPreparedAttempt() throws Exception {
        try (var app = start(new MutableClock())) {
            seed(app, "Consult", false, 30);
            var attempt = prepare(app, "Consult", "2026-11-02T09:00:00+03:00");
            var other = prepare(app, "Consult", "2026-11-02T10:00:00+03:00");
            var path = "/api/booking-attempts/" + attempt.get("attemptId").asString() + "/submit";
            for (var authorization : java.util.Arrays.asList(null, "", "Bearer wrong",
                    "Bearer " + attempt.get("attemptId").asString(),
                    "Bearer " + other.get("attemptSecret").asString(),
                    attempt.get("attemptSecret").asString())) {
                assertError(send(app, "POST", path, null, authorization), 410, "ATTEMPT_UNAVAILABLE");
            }
            var auth = "Bearer " + attempt.get("attemptSecret").asString();
            var before = get(app, "/api/public/owner/meeting-types/Consult/slots");
            for (var body : java.util.List.of("{}",
                    "{\"guestName\":\"Другой\",\"durationMinutes\":480}", "null", "{")) {
                assertError(send(app, "POST", path, body, auth), 400, "MALFORMED_REQUEST");
                assertEquals(before, get(app, "/api/public/owner/meeting-types/Consult/slots"));
            }
            assertError(send(app, "POST", "/api/booking-attempts/unknown/submit", null, auth),
                    410, "ATTEMPT_UNAVAILABLE");
            assertEquals(201, submit(app, attempt).statusCode());
            assertError(send(app, "POST", path, null, "Bearer wrong"), 410, "ATTEMPT_UNAVAILABLE");
            var replay = json.readTree(submit(app, attempt).body());
            assertEquals("Анна 李", replay.get("booking").get("guestName").asString());
            assertEquals(30, replay.get("booking").get("durationMinutes").asInt());
        }
    }

    @Test
    void independentResultLinkReadsCurrentPendingExpiryAtExactStartDespitePublicChanges() throws Exception {
        var clock = new MutableClock();
        try (var app = start(clock)) {
            seed(app, "Consult", true, 30);
            var attempt = prepare(app, "Consult", "2026-11-02T09:00:00+03:00");
            var result = json.readTree(submit(app, attempt).body());
            var path = result.get("resultUrl").asString();
            var token = path.substring("/bookings/".length());
            assertTrue(token.matches("[0-9a-f]{64,}"));
            assertNotEquals(attempt.get("attemptSecret").asString(), token);
            assertNotEquals(attempt.get("attemptId").asString(), token);
            var apiPath = "/api" + path;
            assertEquals(result.get("booking"), get(app, apiPath));
            assertEquals(200, send(app, "PUT", "/api/owner/profile",
                    "{\"name\":\"Новое имя\",\"username\":\"renamed\",\"expectedVersion\":1}", null).statusCode());
            assertEquals(200, send(app, "PUT", "/api/owner/public-booking",
                    "{\"enabled\":false,\"expectedVersion\":2}", null).statusCode());
            clock.set("2026-11-02T05:59:59.999999999Z");
            assertEquals("PENDING", get(app, apiPath).get("status").asString());
            clock.set("2026-11-02T06:00:00Z");
            var response = send(app, "GET", apiPath, null, null);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var current = json.readTree(response.body());
            assertEquals("REJECTED", current.get("status").asString());
            assertEquals("APPROVAL_EXPIRED", current.get("rejectionReason").asString());
            assertEquals(2, current.get("version").asInt());
            assertEquals("Новое имя", current.get("ownerName").asString());
            assertEquals("Консультация Consult", current.get("title").asString());
            assertEquals(current, get(app, apiPath));
            assertEquals(current, json.readTree(submit(app, attempt).body()).get("booking"));
            var missing = send(app, "GET", "/api/bookings/unknown", null, null);
            assertError(missing, 404, "BOOKING_NOT_FOUND");
            assertEquals(json.readTree("""
                    {"code":"BOOKING_NOT_FOUND","message":"Бронирование не найдено"}
                    """), json.readTree(missing.body()));
            assertError(send(app, "GET", "/api/bookings/" + attempt.get("attemptSecret").asString(), null, null),
                    404, "BOOKING_NOT_FOUND");
            var publicPage = get(app, "/api/public/renamed").toString();
            assertFalse(publicPage.contains(token));
            assertFalse(publicPage.contains("guest@example.com"));
        }
    }

    @Test
    void submissionReplayExpiresPendingWithoutPriorResultReadButConfirmedRemainsConfirmed() throws Exception {
        var clock = new MutableClock();
        try (var app = start(clock)) {
            seed(app, "Manual", true, 30);
            seed(app, "Auto", false, 30);
            var pending = prepare(app, "Manual", "2026-11-02T09:00:00+03:00");
            var confirmed = prepare(app, "Auto", "2026-11-02T10:00:00+03:00");
            assertEquals(201, submit(app, pending).statusCode());
            assertEquals(201, submit(app, confirmed).statusCode());
            clock.set("2026-11-02T07:00:00Z");
            assertEquals("REJECTED",
                    json.readTree(submit(app, pending).body()).get("booking").get("status").asString());
            assertEquals("CONFIRMED",
                    json.readTree(submit(app, confirmed).body()).get("booking").get("status").asString());
        }
    }

    @ParameterizedTest
    @CsvSource({"false,09:00", "true,09:15", "false,09:15", "true,09:00"})
    void concurrentCrossTypeSubmissionsReserveSharedTimeAndAllowAdjacency(boolean approval, String secondTime)
            throws Exception {
        try (var app = start(new MutableClock())) {
            seed(app, "Consult", approval, 30);
            seed(app, "Other", approval, 30);
            var first = prepare(app, "Consult", "2026-11-02T09:00:00+03:00");
            var second = prepare(app, "Other", "2026-11-02T" + secondTime + ":00+03:00");
            var before = get(app, "/api/public/owner/meeting-types/Other/slots");
            assertTrue(before.get("items").size() > 0);
            var gate = new java.util.concurrent.CyclicBarrier(2);
            try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var one = pool.submit(() -> {
                    gate.await();
                    return submit(app, first);
                });
                var two = pool.submit(() -> {
                    gate.await();
                    return submit(app, second);
                });
                var responses = java.util.List.of(one.get(), two.get());
                assertEquals(java.util.List.of(201, 409),
                        responses.stream().map(HttpResponse::statusCode).sorted().toList());
                var winner = responses.stream().filter(response -> response.statusCode() == 201)
                        .findFirst().orElseThrow();
                var loser = responses.stream().filter(response -> response.statusCode() == 409)
                        .findFirst().orElseThrow();
                assertError(loser, 409, "TIME_CONFLICT");
                var booked = json.readTree(winner.body()).get("booking");
                assertEquals(approval ? "PENDING" : "CONFIRMED", booked.get("status").asString());
                var after = get(app, "/api/public/owner/meeting-types/Other/slots");
                for (var slot : after.get("items")) {
                    var start = java.time.OffsetDateTime.parse(slot.get("startsAt").asString()).toInstant();
                    var end = java.time.OffsetDateTime.parse(slot.get("endsAt").asString()).toInstant();
                    var bookedStart = java.time.OffsetDateTime.parse(booked.get("startsAt").asString()).toInstant();
                    var bookedEnd = java.time.OffsetDateTime.parse(booked.get("endsAt").asString()).toInstant();
                    assertFalse(start.isBefore(bookedEnd) && bookedStart.isBefore(end));
                }
                var adjacentStart = java.time.OffsetDateTime.parse(booked.get("endsAt").asString()).toString();
                assertEquals(201, submit(app, prepare(app, "Other", adjacentStart)).statusCode());
                var replayFirst = submit(app, first);
                var replaySecond = submit(app, second);
                assertEquals(java.util.List.of(200, 409),
                        java.util.stream.Stream.of(replayFirst, replaySecond)
                        .map(HttpResponse::statusCode).sorted().toList());
            }
        }
    }

    @Test
    void concurrentSubmissionsOfOneAttemptCreateOnlyOneBooking() throws Exception {
        try (var app = start(new MutableClock())) {
            seed(app, "Consult", false, 30);
            var attempt = prepare(app, "Consult", "2026-11-02T09:00:00+03:00");
            var gate = new java.util.concurrent.CyclicBarrier(2);
            try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var one = pool.submit(() -> {
                    gate.await();
                    return submit(app, attempt);
                });
                var two = pool.submit(() -> {
                    gate.await();
                    return submit(app, attempt);
                });
                var first = one.get();
                var second = two.get();
                assertEquals(java.util.List.of(200, 201),
                        java.util.stream.Stream.of(first, second).map(HttpResponse::statusCode).sorted().toList());
                assertEquals(json.readTree(first.body()), json.readTree(second.body()));
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
        "2026-11-01T09:00:00+03:00, OUTSIDE_BOOKING_WINDOW",
        "2026-11-16T09:00:00+03:00, OUTSIDE_BOOKING_WINDOW",
        "2026-11-15T23:45:00+03:00, OUTSIDE_BOOKING_WINDOW",
        "2026-11-02T09:01:00+03:00, SLOT_UNAVAILABLE",
        "2026-11-02T09:00:01+03:00, SLOT_UNAVAILABLE",
        "2026-11-02T09:00:00.001+03:00, SLOT_UNAVAILABLE",
        "2026-11-02T08:45:00+03:00, SLOT_UNAVAILABLE",
        "2026-11-02T17:45:00+03:00, SLOT_UNAVAILABLE",
        "2026-11-07T09:00:00+03:00, SLOT_UNAVAILABLE"
    })
    void rechecksWindowGridAndFullContinuousAvailabilityWithoutPartialBooking(String start, String code)
            throws Exception {
        try (var app = start(new MutableClock())) {
            seed(app, "Consult", false, 30);
            var before = get(app, "/api/public/owner/meeting-types/Consult/slots");
            var attempt = prepare(app, "Consult", start);
            assertError(submit(app, attempt), 409, code);
            assertError(submit(app, attempt), 409, code);
            assertEquals(before, get(app, "/api/public/owner/meeting-types/Consult/slots"));
        }
    }

    @Test
    void recalculatesWindowAfterOwnerMidnightAndKeepsItsRefusal() throws Exception {
        var clock = new MutableClock();
        clock.set("2026-11-01T20:59:59Z");
        try (var app = start(clock)) {
            seed(app, "Consult", false, 30);
            var attempt = prepare(app, "Consult", "2026-11-02T09:00:00+03:00");
            clock.set("2026-11-01T21:00:00Z");
            assertError(submit(app, attempt), 409, "OUTSIDE_BOOKING_WINDOW");
            clock.set("2026-11-01T20:59:59Z");
            assertError(submit(app, attempt), 409, "OUTSIDE_BOOKING_WINDOW");
        }
    }

    @Test
    void savesRefusalAndChecksSavedOutcomesBeforeChangedPublicState() throws Exception {
        try (var app = start(new MutableClock())) {
            seed(app, "Consult", false, 30);
            var success = prepare(app, "Consult", "2026-11-02T09:00:00+03:00");
            assertEquals(201, submit(app, success).statusCode());
            var refused = prepare(app, "Consult", "2026-11-02T10:00:00+03:00");
            var staleAddress = prepare(app, "Consult", "2026-11-02T11:00:00+03:00");
            assertEquals(200, send(app, "PUT", "/api/owner/public-booking",
                    "{\"enabled\":false,\"expectedVersion\":1}", null).statusCode());
            var failure = submit(app, refused);
            assertError(failure, 409, "BOOKING_DISABLED");
            assertEquals(200, send(app, "PUT", "/api/owner/profile",
                    "{\"name\":\"Новое имя\",\"username\":\"renamed\",\"expectedVersion\":2}", null).statusCode());
            assertError(submit(app, staleAddress), 404, "PAGE_NOT_FOUND");
            assertEquals(failure.body(), submit(app, refused).body());
            assertEquals(200, send(app, "PUT", "/api/owner/public-booking",
                    "{\"enabled\":true,\"expectedVersion\":3}", null).statusCode());
            assertEquals(failure.body(), submit(app, refused).body());
            assertEquals(200, submit(app, success).statusCode());
        }
    }

    private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        var error = json.readTree(response.body());
        assertEquals(code, error.get("code").asString());
        assertFalse(error.get("message").asString().isBlank());
        assertFalse(response.body().contains("guest@example.com"));
        assertFalse(response.body().contains("attemptSecret"));
        assertFalse(response.body().contains("resultUrl"));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
    }

    @Test
    void submitsImmutableContactsAndConditionsOnceAndReplaysTheSameBooking() throws Exception {
        try (var app = start(new MutableClock())) {
            seed(app, "Consult", false, 30);
            var attempt = prepare(app, "Consult", "2026-11-02T09:00:00+03:00");
            var response = submit(app, attempt);
            assertEquals(201, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var result = json.readTree(response.body());
            assertEquals("SUCCEEDED", result.get("state").asString());
            var booking = result.get("booking");
            assertEquals("CONFIRMED", booking.get("status").asString());
            assertEquals("Консультация Consult", booking.get("title").asString());
            assertEquals("Подробное описание консультации", booking.get("description").asString());
            assertEquals(30, booking.get("durationMinutes").asInt());
            assertFalse(booking.get("requiresApproval").asBoolean());
            assertEquals(Instant.parse("2026-11-02T06:00:00Z"),
                    java.time.OffsetDateTime.parse(booking.get("startsAt").asString()).toInstant());
            assertEquals(Instant.parse("2026-11-02T06:30:00Z"),
                    java.time.OffsetDateTime.parse(booking.get("endsAt").asString()).toInstant());
            assertEquals("Анна 李", booking.get("guestName").asString());
            assertEquals("guest@example.com", booking.get("guestEmail").asString());
            assertEquals("Вопрос\nВторая строка", booking.get("comment").asString());
            assertEquals(1, booking.get("version").asInt());
            var replay = submit(app, attempt);
            assertEquals(200, replay.statusCode(), replay.body());
            assertEquals(result, json.readTree(replay.body()));
        }
    }

    private ConfigurableApplicationContext start(MutableClock clock, String... settings) {
        var args = new java.util.ArrayList<>(java.util.List.of(
                "--server.port=0", "--calendar.initial.booking-enabled=true"));
        args.addAll(java.util.List.of(settings));
        return new SpringApplicationBuilder(CalendarApplication.class)
                .initializers(context -> context.getBeanFactory().registerSingleton("testClock", clock))
                .run(args.toArray(String[]::new));
    }

    private void seed(ConfigurableApplicationContext app, String id, boolean approval, int duration) throws Exception {
        var response = send(app, "POST", "/api/owner/meeting-types", json.writeValueAsString(Map.of(
                "id", id, "title", "Консультация " + id, "description", "Подробное описание консультации",
                "durationMinutes", duration, "requiresApproval", approval)), null);
        assertEquals(201, response.statusCode(), response.body());
    }

    private JsonNode prepare(ConfigurableApplicationContext app, String id, String start) throws Exception {
        var slots = get(app, "/api/public/owner/meeting-types/" + id + "/slots");
        var response = send(app, "POST", "/api/public/owner/booking-attempts", json.writeValueAsString(Map.of(
                "meetingTypeId", id, "conditionsToken", slots.get("conditionsToken").asString(),
                "startsAt", start, "guestName", "  Анна\u00a0李  ", "guestEmail", " guest@example.com ",
                "comment", "Вопрос\nВторая строка")), null);
        assertEquals(201, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private HttpResponse<String> submit(ConfigurableApplicationContext app, JsonNode attempt) throws Exception {
        return send(app, "POST", "/api/booking-attempts/" + attempt.get("attemptId").asString() + "/submit",
                null, "Bearer " + attempt.get("attemptSecret").asString());
    }

    private JsonNode get(ConfigurableApplicationContext app, String path) throws Exception {
        var response = send(app, "GET", path, null, null);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private HttpResponse<String> send(ConfigurableApplicationContext app, String method, String path, String body,
            String authorization) throws Exception {
        int port = ((WebServerApplicationContext) app).getWebServer().getPort();
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json");
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        if (path.startsWith("/api/owner/") && !method.equals("GET")) {
            builder.header("Idempotency-Key", UUID.randomUUID().toString());
        }
        try (var client = HttpClient.newHttpClient()) {
            return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    static final class MutableClock extends Clock {
        private volatile Instant now = Instant.parse("2026-11-01T00:00:00Z");

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
