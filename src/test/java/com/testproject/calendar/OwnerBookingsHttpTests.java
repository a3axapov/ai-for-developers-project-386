package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OwnerBookingsHttpTests {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void malformedQueriesAndInvalidRangesReturnContractErrorsWithoutChangingBookings() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var booking = book(app, "Auto", false, "2026-11-02T09:00:00+03:00").get("booking");
            for (var path : List.of("/api/owner/bookings", "/api/owner/calendar",
                    "/api/owner/calendar?from=2026-11-02T09:00:00&to=2026-11-03T00:00:00Z",
                    "/api/owner/calendar?from=not-a-date&to=2026-11-03T00:00:00Z")) {
                var response = send(app, "GET", path, null, null);
                assertEquals(400, response.statusCode(), response.body());
                assertEquals("MALFORMED_REQUEST", json.readTree(response.body()).path("code").asString());
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            }
            for (var path : List.of("/api/owner/bookings?view=unknown", "/api/owner/bookings?view=",
                    "/api/owner/bookings?view=upcoming&status=unknown",
                    "/api/owner/calendar?from=2026-11-03T00:00:00Z&to=2026-11-02T00:00:00Z",
                    "/api/owner/calendar?from=2026-11-02T00:00:00Z&to=2026-11-02T00:00:00Z")) {
                var response = send(app, "GET", path, null, null);
                assertEquals(422, response.statusCode(), response.body());
                assertEquals("VALIDATION_FAILED", json.readTree(response.body()).get("code").asString());
                assertEquals(1, json.readTree(response.body()).get("fieldErrors").size());
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            }
            assertEquals(booking, get(app, "/api/owner/bookings/" + booking.get("id").asString()));
            for (var path : List.of("/api/public/owner", "/api/public/owner/meeting-types/Auto",
                    "/api/public/owner/meeting-types/Auto/slots")) {
                var body = get(app, path).toString();
                assertFalse(body.contains("guest@example.com"));
                assertFalse(body.contains("Анна 李"));
                assertFalse(body.contains("accessToken"));
            }
        }
    }

    @Test
    void calendarExistsInitiallyAndSelectsActualHalfOpenOverlapIncludingDstAndExpiredBookings() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        clock.set("2026-10-24T00:00:00Z");
        try (var app = start(clock, "--calendar.initial.time-zone=Europe/Berlin",
                "--calendar.initial.working-days=SUNDAY", "--calendar.initial.work-start=00:00",
                "--calendar.initial.work-end=24:00")) {
            var range = "/api/owner/calendar?from=2026-10-25T00:15:00Z&to=2026-10-25T01:15:00Z";
            assertEquals(0, get(app, range).get("items").size());
            var first = book(app, "First", false, "2026-10-25T02:00:00+02:00");
            var second = book(app, "Second", true, "2026-10-25T02:00:00+01:00");
            var items = get(app, range).get("items");
            assertEquals(2, items.size());
            assertEquals(first.get("booking"), items.get(0));
            assertEquals(second.get("booking"), items.get(1));
            assertEquals(0, get(app,
                    "/api/owner/calendar?from=2026-10-25T00:30:00Z&to=2026-10-25T01:00:00Z")
                    .get("items").size());
            assertEquals(1, get(app,
                    "/api/owner/calendar?from=2026-10-25T02:00:00%2B01:00&to=2026-10-25T02:30:00%2B01:00")
                    .get("items").size());
            clock.set("2026-10-25T01:00:00Z");
            var expired = get(app, range).get("items").get(1);
            assertEquals("REJECTED", expired.get("status").asString());
            assertEquals("APPROVAL_EXPIRED", expired.get("rejectionReason").asString());
            assertEquals(expired, get(app, "/api/owner/bookings/" + expired.get("id").asString()));
        }
    }

    @Test
    void ownerDetailsRefreshExpiryAtExactStartAndHistoryKeepsStartedConfirmed() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        try (var app = start(clock)) {
            var pending = book(app, "Manual", true, "2026-11-02T09:00:00+03:00");
            var confirmed = book(app, "Auto", false, "2026-11-02T09:30:00+03:00");
            var path = "/api/owner/bookings/" + pending.get("booking").get("id").asString();
            assertEquals(pending.get("booking"), get(app, path));
            clock.set("2026-11-02T05:59:59.999999999Z");
            assertEquals(2, get(app, "/api/owner/bookings?view=upcoming").get("items").size());
            clock.set("2026-11-02T06:00:00Z");
            var expired = get(app, path);
            assertEquals("REJECTED", expired.get("status").asString());
            assertEquals("APPROVAL_EXPIRED", expired.get("rejectionReason").asString());
            assertEquals(2, expired.get("version").asInt());
            assertEquals(expired, get(app, path));
            assertEquals(expired, get(app, "/api/owner/bookings?view=history").get("items").get(0));
            assertEquals(0, get(app, "/api/owner/bookings?view=upcoming&status=PENDING").get("items").size());
            // Rewind only the controllable test clock: a terminal booking remains history even with a future start.
            clock.set("2026-11-02T05:00:00Z");
            assertEquals(expired,
                    get(app, "/api/owner/bookings?view=history&status=REJECTED").get("items").get(0));
            assertEquals(1, get(app, "/api/owner/bookings?view=upcoming").get("items").size());
            clock.set("2026-11-02T06:30:00Z");
            assertEquals(0, get(app, "/api/owner/bookings?view=upcoming").get("items").size());
            var history = get(app, "/api/owner/bookings?view=history").get("items");
            assertEquals(2, history.size());
            assertEquals(confirmed.get("booking"), history.get(1));
            assertEquals(expired, get(app, "/api/bookings/" + pending.get("resultUrl").asString()
                    .substring("/bookings/".length())));
            var unknown = send(app, "GET", "/api/owner/bookings/unknown", null, null);
            assertEquals(404, unknown.statusCode());
            assertEquals(json.readTree("""
                    {"code":"BOOKING_NOT_FOUND","message":"Бронирование не найдено"}
                    """), json.readTree(unknown.body()));
            assertEquals("no-store", unknown.headers().firstValue("Cache-Control").orElseThrow());
        }
    }

    @Test
    void unifiedUpcomingIncludesAllTypesInStartOrderAndPendingIsOnlyAFilter() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var later = book(app, "Auto", false, "2026-11-02T10:00:00+03:00");
            var earlier = book(app, "Manual", true, "2026-11-02T09:00:00+03:00");
            var response = send(app, "GET", "/api/owner/bookings?view=upcoming", null, null);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var items = json.readTree(response.body()).get("items");
            assertEquals(List.of(earlier.get("booking"), later.get("booking")),
                    java.util.stream.StreamSupport.stream(items.spliterator(), false).toList());
            assertEquals(earlier.get("booking"),
                    get(app, "/api/owner/bookings?view=upcoming&status=PENDING").get("items").get(0));
            assertEquals(2, get(app, "/api/owner/bookings?view=upcoming").get("items").size());
            assertEquals(0, get(app, "/api/owner/bookings?view=history").get("items").size());
            for (var result : List.of(earlier, later)) {
                assertFalse(response.body().contains(result.get("resultUrl").asString()));
                assertFalse(response.body().contains("accessToken"));
            }
        }
    }

    private ConfigurableApplicationContext start(BookingSubmissionHttpTests.MutableClock clock, String... settings) {
        var args = new java.util.ArrayList<>(List.of(
                "--server.port=0", "--calendar.initial.booking-enabled=true"));
        args.addAll(List.of(settings));
        return new SpringApplicationBuilder(CalendarApplication.class)
                .initializers(context -> context.getBeanFactory().registerSingleton("testClock", clock))
                .run(args.toArray(String[]::new));
    }

    private JsonNode book(ConfigurableApplicationContext app, String id, boolean approval, String start)
            throws Exception {
        var type = send(app, "POST", "/api/owner/meeting-types", json.writeValueAsString(Map.of(
                "id", id, "title", "Консультация " + id, "description", "Подробное описание консультации",
                "durationMinutes", 30, "requiresApproval", approval)), null);
        assertEquals(201, type.statusCode(), type.body());
        var slots = get(app, "/api/public/owner/meeting-types/" + id + "/slots");
        var prepared = send(app, "POST", "/api/public/owner/booking-attempts", json.writeValueAsString(Map.of(
                "meetingTypeId", id, "conditionsToken", slots.get("conditionsToken").asString(),
                "startsAt", start, "guestName", "Анна 李", "guestEmail", "guest@example.com",
                "comment", "Вопрос\nВторая строка")), null);
        assertEquals(201, prepared.statusCode(), prepared.body());
        var attempt = json.readTree(prepared.body());
        var submitted = send(app, "POST", "/api/booking-attempts/" + attempt.get("attemptId").asString() + "/submit",
                null, "Bearer " + attempt.get("attemptSecret").asString());
        assertEquals(201, submitted.statusCode(), submitted.body());
        return json.readTree(submitted.body());
    }

    private JsonNode get(ConfigurableApplicationContext app, String path) throws Exception {
        var response = send(app, "GET", path, null, null);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
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
}
