package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OwnerCancellationHttpTests {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void cancellationKeepsDetailsInHistoryCalendarAndGuestResultAndReleasesOnlyItsOccupancy() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var saved = book(app, "Auto", false, "2026-11-02T09:00:00+03:00");
            var other = book(app, "Other", false, "2026-11-02T09:30:00+03:00").get("booking");
            var booking = saved.get("booking");
            var response = cancel(app, booking, "cancel");
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var cancelled = json.readTree(response.body());
            var expected = booking.deepCopy();
            ((tools.jackson.databind.node.ObjectNode) expected).put("status", "CANCELLED").put("version", 2);
            assertEquals(expected, cancelled);
            assertEquals(cancelled, get(app, "/api/owner/bookings/" + booking.get("id").asString()));
            assertEquals(cancelled, get(app, "/api" + saved.get("resultUrl").asString()));
            assertEquals(cancelled, get(app, "/api/owner/bookings?view=history").get("items").get(0));
            assertEquals(other, get(app, "/api/owner/bookings?view=upcoming").get("items").get(0));
            assertEquals(cancelled, get(app,
                    "/api/owner/calendar?from=2026-11-02T06:00:00Z&to=2026-11-02T06:30:00Z").get("items").get(0));
            var slots = get(app, "/api/public/owner/meeting-types/Auto/slots").get("items")
                    .valueStream().map(slot -> slot.get("startsAt").asString()).toList();
            assertEquals(true, slots.contains("2026-11-02T09:00:00+03:00"));
            assertFalse(slots.contains("2026-11-02T09:30:00+03:00"));
            assertFalse(slots.contains("2026-11-02T08:45:00+03:00"));
            var outsideHours = send(app, "POST", "/api/public/owner/booking-attempts", json.writeValueAsString(Map.of(
                    "meetingTypeId", "Auto", "conditionsToken",
                    get(app, "/api/public/owner/meeting-types/Auto/slots").get("conditionsToken").asString(),
                    "startsAt", "2026-11-02T08:45:00+03:00",
                    "guestName", "Другой гость", "guestEmail", "other@example.com")), null);
            assertEquals(201, outsideHours.statusCode());
            var unavailable = submit(app, json.readTree(outsideHours.body()));
            assertEquals(409, unavailable.statusCode());
            assertEquals("SLOT_UNAVAILABLE", json.readTree(unavailable.body()).get("code").asString());
            var replacement = book(app, "Replacement", false, "2026-11-02T09:00:00+03:00");
            assertEquals("CONFIRMED", replacement.get("booking").get("status").asString());
            assertEquals(cancelled, json.readTree(submit(app, saved.get("attempt")).body()).get("booking"));
            assertEquals(cancelled, get(app,
                    "/api/booking-attempts/" + saved.get("attempt").get("attemptId").asString(),
                    "Bearer " + saved.get("attempt").get("attemptSecret").asString()).get("booking"));
            assertEquals(2, get(app, "/api/owner/bookings?view=upcoming").get("items").size());
        }
    }

    @Test
    void rejectedBookingCannotBeCancelledAndStaleCancellationAfterConfirmationRequiresNewDecision() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var saved = book(app, "Manual", true, "2026-11-02T09:00:00+03:00");
            var booking = saved.get("booking");
            var path = "/api/owner/bookings/" + booking.get("id").asString();
            var rejected = send(app, "POST", path + "/reject", "{\"expectedVersion\":1}", "reject");
            assertEquals(200, rejected.statusCode());
            var terminal = json.readTree(rejected.body());
            var response = cancel(app, terminal, "cancel");
            assertEquals(409, response.statusCode());
            assertEquals("ACTION_NOT_ALLOWED", json.readTree(response.body()).get("code").asString());
            assertEquals(terminal, get(app, path));
            assertEquals(terminal, get(app, "/api" + saved.get("resultUrl").asString()));
            var second = book(app, "Second", true, "2026-11-02T09:00:00+03:00");
            var original = second.get("booking");
            var confirmed = send(app, "POST", "/api/owner/bookings/" + original.get("id").asString() + "/confirm",
                    "{\"expectedVersion\":1}", "confirm");
            assertEquals(200, confirmed.statusCode());
            var stale = cancel(app, original, "stale-cancel");
            assertEquals(409, stale.statusCode());
            assertEquals("RESOURCE_CHANGED", json.readTree(stale.body()).get("code").asString());
            assertEquals(json.readTree(confirmed.body()), json.readTree(stale.body()).get("currentBooking"));
            assertEquals(json.readTree(confirmed.body()), get(app, "/api" + second.get("resultUrl").asString()));
            assertEquals(200, cancel(app, json.readTree(confirmed.body()), "new-decision").statusCode());
            assertEquals(json.readTree(stale.body()), get(app, "/api/owner/operations/stale-cancel").get("error"));
        }
    }

    @Test
    void strictStartBoundaryAllowsLastInstantButRefusesExactStartAndPastWithoutAnyChange() throws Exception {
        for (var now : List.of("2026-11-02T05:59:59.999999999Z", "2026-11-02T06:00:00Z",
                "2026-11-03T06:00:00Z")) {
            var clock = new BookingSubmissionHttpTests.MutableClock();
            try (var app = start(clock)) {
                var saved = book(app, "Auto", false, "2026-11-02T09:00:00+03:00");
                var booking = saved.get("booking");
                clock.set(now);
                var response = cancel(app, booking, "boundary");
                boolean allowed = now.equals("2026-11-02T05:59:59.999999999Z");
                assertEquals(allowed ? 200 : 409, response.statusCode(), response.body());
                var result = json.readTree(response.body());
                if (allowed) {
                    assertEquals("CANCELLED", result.get("status").asString());
                    assertEquals(2, result.get("version").asInt());
                } else {
                    assertEquals("ACTION_NOT_ALLOWED", result.get("code").asString());
                    assertEquals(booking, result.get("currentBooking"));
                    assertEquals(booking, get(app, "/api" + saved.get("resultUrl").asString()));
                }
                clock.set("2026-11-01T00:00:00Z");
                assertEquals(result, json.readTree(cancel(app, booking, "boundary").body()));
                assertEquals(result, get(app, "/api/owner/operations/boundary").get(allowed ? "booking" : "error"));
            }
        }
    }

    @Test
    void manuallyConfirmedArchivedBookingCanBeCancelledWithPublicBookingDisabledAndAttemptReplaysSameBooking()
            throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        try (var app = start(clock)) {
            var saved = book(app, "Manual", true, "2026-11-02T09:00:00+03:00");
            var booking = saved.get("booking");
            var pending = cancel(app, booking, "pending");
            assertEquals(409, pending.statusCode());
            assertEquals("ACTION_NOT_ALLOWED", json.readTree(pending.body()).get("code").asString());
            assertEquals(booking, get(app, "/api/owner/bookings/" + booking.get("id").asString()));
            var confirmedResponse = send(app, "POST",
                    "/api/owner/bookings/" + booking.get("id").asString() + "/confirm",
                    "{\"expectedVersion\":1}", "confirm");
            assertEquals(200, confirmedResponse.statusCode());
            var confirmed = json.readTree(confirmedResponse.body());
            assertEquals(200, send(app, "POST", "/api/owner/meeting-types/Manual/archive",
                    "{\"expectedVersion\":1}", "archive").statusCode());
            assertEquals(200, send(app, "PUT", "/api/owner/public-booking",
                    "{\"enabled\":false,\"expectedVersion\":1}", "disable").statusCode());
            clock.set("2026-11-02T05:59:59.999999999Z");
            var response = cancel(app, confirmed, "cancel");
            assertEquals(200, response.statusCode(), response.body());
            var cancelled = json.readTree(response.body());
            assertEquals("CANCELLED", cancelled.get("status").asString());
            assertEquals(3, cancelled.get("version").asInt());
            assertEquals(true, cancelled.get("requiresApproval").asBoolean());
            assertEquals(cancelled, get(app, "/api" + saved.get("resultUrl").asString()));
            clock.set("2026-11-04T00:00:00Z");
            var replay = submit(app, saved.get("attempt"));
            assertEquals(200, replay.statusCode());
            assertEquals(cancelled, json.readTree(replay.body()).get("booking"));
            assertEquals(saved.get("resultUrl"), json.readTree(replay.body()).get("resultUrl"));
            assertEquals(0, get(app, "/api/owner/bookings?view=upcoming").get("items").size());
            assertEquals(1, get(app, "/api/owner/bookings?view=history").get("items").size());
            // Retained failure/success snapshots must not follow later changes to the live booking.
            assertEquals(json.readTree(pending.body()), get(app, "/api/owner/operations/pending").get("error"));
            assertEquals(confirmed, get(app, "/api/owner/operations/confirm").get("booking"));
            assertEquals(cancelled, get(app, "/api/owner/operations/cancel").get("booking"));
        }
    }

    @Test
    void invalidStaleAndReusedCommandsAreRetainedWithoutPartialChanges() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var saved = book(app, "Auto", false, "2026-11-02T09:00:00+03:00");
            var booking = saved.get("booking");
            var path = "/api/owner/bookings/" + booking.get("id").asString();
            for (var body : List.of("{}", "{\"expectedVersion\":null}", "{\"expectedVersion\":0}",
                    "{\"expectedVersion\":-1}", "{\"expectedVersion\":1.5}", "{broken", "")) {
                var key = UUID.randomUUID().toString();
                var response = send(app, "POST", path + "/cancel", body, key);
                boolean malformed = body.contains("1.5") || body.equals("{broken") || body.isEmpty();
                assertEquals(malformed ? 400 : 422, response.statusCode(), response.body());
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
                var error = json.readTree(response.body());
                assertEquals(malformed ? "MALFORMED_REQUEST" : "VALIDATION_FAILED", error.get("code").asString());
                if (!malformed) {
                    assertEquals("expectedVersion", error.get("fieldErrors").get(0).get("field").asString());
                }
                assertEquals(error, json.readTree(send(app, "POST", path + "/cancel", body, key).body()));
                assertEquals(error, get(app, "/api/owner/operations/" + key).get("error"));
                var reused = cancel(app, booking, key);
                assertEquals(409, reused.statusCode());
                assertEquals("IDEMPOTENCY_KEY_REUSED", json.readTree(reused.body()).get("code").asString());
                assertEquals(booking, get(app, path));
            }
            for (var key : java.util.Arrays.asList(null, "bad.key", "x".repeat(129))) {
                assertEquals(400, cancel(app, booking, key).statusCode());
                assertEquals(booking, get(app, path));
            }
            var stale = send(app, "POST", path + "/cancel", "{\"expectedVersion\":2}", "stale");
            assertEquals(409, stale.statusCode());
            var staleError = json.readTree(stale.body());
            assertEquals("RESOURCE_CHANGED", staleError.get("code").asString());
            assertEquals(booking, staleError.get("currentBooking"));
            var missing = send(app, "POST", "/api/owner/bookings/unknown/cancel", "{\"expectedVersion\":1}", "missing");
            assertEquals(404, missing.statusCode());
            assertEquals("BOOKING_NOT_FOUND", json.readTree(missing.body()).get("code").asString());
            var cancelled = cancel(app, booking, "cancel");
            assertEquals(200, cancelled.statusCode());
            var current = json.readTree(cancelled.body());
            assertEquals(current, json.readTree(cancel(app, booking, "cancel").body()));
            for (var otherPath : List.of(path + "/reject", path + "/confirm", "/api/owner/bookings/other/cancel")) {
                var reused = send(app, "POST", otherPath, "{\"expectedVersion\":1}", "cancel");
                assertEquals(409, reused.statusCode());
                assertEquals("IDEMPOTENCY_KEY_REUSED", json.readTree(reused.body()).get("code").asString());
            }
            var crossResource = send(app, "PUT", "/api/owner/public-booking",
                    "{\"enabled\":false,\"expectedVersion\":1}", "cancel");
            assertEquals(409, crossResource.statusCode());
            assertEquals(true, get(app, "/api/owner/profile").get("bookingEnabled").asBoolean());
            for (var action : List.of("confirm", "reject", "cancel")) {
                var terminal = send(app, "POST", path + "/" + action, "{\"expectedVersion\":2}", "terminal-" + action);
                assertEquals(409, terminal.statusCode());
                assertEquals("ACTION_NOT_ALLOWED", json.readTree(terminal.body()).get("code").asString());
            }
            assertEquals(current, get(app, path));
            assertEquals(staleError, get(app, "/api/owner/operations/stale").get("error"));
            assertEquals(staleError, json.readTree(
                    send(app, "POST", path + "/cancel", "{\"expectedVersion\":2}", "stale").body()));
        }
    }

    @Test
    void concurrentCancellationsCommitOnceAndRetainImmutableWinnerAndStaleLoser() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var saved = book(app, "Auto", false, "2026-11-02T09:00:00+03:00");
            var booking = saved.get("booking");
            var ready = new java.util.concurrent.CountDownLatch(2);
            var go = new java.util.concurrent.CountDownLatch(1);
            try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var tasks = List.of("a", "b").stream().map(key -> executor.submit(() -> {
                    ready.countDown();
                    if (!go.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent cancellation did not start");
                    }
                    return cancel(app, booking, key);
                })).toList();
                assertEquals(true, ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
                go.countDown();
                var responses = List.of(tasks.get(0).get(), tasks.get(1).get());
                assertEquals(List.of(200, 409), responses.stream().map(HttpResponse::statusCode).sorted().toList());
                var current = get(app, "/api/owner/bookings/" + booking.get("id").asString());
                assertEquals("CANCELLED", current.get("status").asString());
                assertEquals(2, current.get("version").asInt());
                for (int i = 0; i < 2; i++) {
                    var response = responses.get(i);
                    var result = json.readTree(response.body());
                    if (response.statusCode() == 409) {
                        assertEquals("RESOURCE_CHANGED", result.get("code").asString());
                        assertEquals(current, result.get("currentBooking"));
                    } else {
                        assertEquals(current, result);
                    }
                    var key = List.of("a", "b").get(i);
                    assertEquals(result, json.readTree(cancel(app, booking, key).body()));
                    var operation = get(app, "/api/owner/operations/" + key);
                    assertEquals(response.statusCode(), operation.get("statusCode").asInt());
                    assertEquals(result, operation.get(response.statusCode() == 200 ? "booking" : "error"));
                }
                assertEquals(current, json.readTree(submit(app, saved.get("attempt")).body()).get("booking"));
            }
        }
    }

    private ConfigurableApplicationContext start(BookingSubmissionHttpTests.MutableClock clock) {
        return new SpringApplicationBuilder(CalendarApplication.class)
                .initializers(context -> context.getBeanFactory().registerSingleton("testClock", clock))
                .run("--server.port=0", "--calendar.initial.booking-enabled=true");
    }

    private JsonNode book(ConfigurableApplicationContext app, String id, boolean approval, String start)
            throws Exception {
        var type = send(app, "POST", "/api/owner/meeting-types", json.writeValueAsString(Map.of(
                "id", id, "title", "Консультация " + id, "description", "Подробное описание консультации",
                "durationMinutes", 30, "requiresApproval", approval)), UUID.randomUUID().toString());
        assertEquals(201, type.statusCode(), type.body());
        var slots = get(app, "/api/public/owner/meeting-types/" + id + "/slots");
        var prepared = send(app, "POST", "/api/public/owner/booking-attempts", json.writeValueAsString(Map.of(
                "meetingTypeId", id, "conditionsToken", slots.get("conditionsToken").asString(),
                "startsAt", start, "guestName", "Анна 李", "guestEmail", "guest@example.com",
                "comment", "Вопрос\nВторая строка")), null);
        assertEquals(201, prepared.statusCode(), prepared.body());
        var attempt = json.readTree(prepared.body());
        var submitted = submit(app, attempt);
        assertEquals(201, submitted.statusCode(), submitted.body());
        var result = json.readTree(submitted.body());
        ((tools.jackson.databind.node.ObjectNode) result).set("attempt", attempt);
        return result;
    }

    private HttpResponse<String> submit(ConfigurableApplicationContext app, JsonNode attempt) throws Exception {
        return send(app, "POST", "/api/booking-attempts/" + attempt.get("attemptId").asString() + "/submit",
                null, null, "Bearer " + attempt.get("attemptSecret").asString());
    }

    private HttpResponse<String> cancel(ConfigurableApplicationContext app, JsonNode booking, String key)
            throws Exception {
        return send(app, "POST", "/api/owner/bookings/" + booking.get("id").asString() + "/cancel",
                "{\"expectedVersion\":" + booking.get("version").asInt() + "}", key);
    }

    private JsonNode get(ConfigurableApplicationContext app, String path) throws Exception {
        return get(app, path, null);
    }

    private JsonNode get(ConfigurableApplicationContext app, String path, String authorization) throws Exception {
        var response = send(app, "GET", path, null, null, authorization);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        return json.readTree(response.body());
    }

    private HttpResponse<String> send(ConfigurableApplicationContext app, String method, String path, String body,
            String key) throws Exception {
        return send(app, method, path, body, key, null);
    }

    private HttpResponse<String> send(ConfigurableApplicationContext app, String method, String path, String body,
            String key, String authorization) throws Exception {
        int port = ((WebServerApplicationContext) app).getWebServer().getPort();
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json");
        if (key != null) {
            builder.header("Idempotency-Key", key);
        }
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        try (var client = HttpClient.newHttpClient()) {
            return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
