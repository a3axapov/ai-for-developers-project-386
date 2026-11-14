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

class OwnerDecisionsHttpTests {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void invalidAndStaleCommandsAreRetainedAndNeverPartiallyChangeBookingOrOccupancy() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var saved = book(app, "Manual", true, "2026-11-02T09:00:00+03:00");
            var booking = saved.result().get("booking");
            var path = "/api/owner/bookings/" + booking.get("id").asString();
            for (var action : List.of("confirm", "reject")) {
                for (var body : List.of("{}", "{\"expectedVersion\":0}", "{\"expectedVersion\":null}",
                        "{\"expectedVersion\":-1}", "{\"expectedVersion\":1.5}", "{broken", "")) {
                    var key = UUID.randomUUID().toString();
                    var response = send(app, "POST", path + "/" + action, body, key);
                    boolean malformed = body.contains("1.5") || body.equals("{broken") || body.isEmpty();
                    assertEquals(malformed ? 400 : 422, response.statusCode(), response.body());
                    var error = json.readTree(response.body());
                    assertEquals(malformed ? "MALFORMED_REQUEST" : "VALIDATION_FAILED",
                            error.get("code").asString());
                    assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
                    if (!malformed) {
                        assertEquals("expectedVersion", error.get("fieldErrors").get(0).get("field").asString());
                    }
                    assertEquals(error, json.readTree(send(app, "POST", path + "/" + action, body, key).body()));
                    assertEquals(error, get(app, "/api/owner/operations/" + key).get("error"));
                    var reused = decide(app, booking, action, key);
                    assertEquals(409, reused.statusCode());
                    assertEquals("IDEMPOTENCY_KEY_REUSED", json.readTree(reused.body()).get("code").asString());
                    assertEquals(booking, get(app, path));
                }
                for (var key : java.util.Arrays.asList(null, "bad.key", "x".repeat(129))) {
                    var response = decide(app, booking, action, key);
                    assertEquals(400, response.statusCode(), response.body());
                    assertEquals("MALFORMED_REQUEST", json.readTree(response.body()).get("code").asString());
                }
                var stale = send(app, "POST", path + "/" + action, "{\"expectedVersion\":2}", "stale-" + action);
                assertEquals(409, stale.statusCode());
                assertEquals("RESOURCE_CHANGED", json.readTree(stale.body()).get("code").asString());
                assertEquals(booking, json.readTree(stale.body()).get("currentBooking"));
                var missing = send(app, "POST", "/api/owner/bookings/unknown/" + action,
                        "{\"expectedVersion\":1}", "missing-" + action);
                assertEquals(404, missing.statusCode());
                assertEquals("BOOKING_NOT_FOUND", json.readTree(missing.body()).get("code").asString());
                assertEquals(booking, get(app, path));
            }
            assertFalse(get(app, "/api/public/owner/meeting-types/Manual/slots").get("items").toString()
                    .contains("2026-11-02T09:00:00+03:00"));
            var confirmed = decide(app, booking, "confirm", "shared");
            assertEquals(200, confirmed.statusCode());
            assertEquals("no-store", confirmed.headers().firstValue("Cache-Control").orElseThrow());
            for (var requestPath : List.of(path + "/reject", "/api/owner/bookings/other/confirm",
                    "/api/owner/bookings/other/reject")) {
                var reused = send(app, "POST", requestPath, "{\"expectedVersion\":1}", "shared");
                assertEquals(409, reused.statusCode());
                assertEquals("IDEMPOTENCY_KEY_REUSED", json.readTree(reused.body()).get("code").asString());
            }
            var crossResource = send(app, "PUT", "/api/owner/public-booking",
                    "{\"enabled\":false,\"expectedVersion\":1}", "shared");
            assertEquals(409, crossResource.statusCode());
            assertEquals(true, get(app, "/api/owner/profile").get("bookingEnabled").asBoolean());
            var current = json.readTree(confirmed.body());
            for (var action : List.of("confirm", "reject")) {
                var notAllowed = decide(app, current, action, "terminal-" + action);
                assertEquals(409, notAllowed.statusCode());
                assertEquals("ACTION_NOT_ALLOWED", json.readTree(notAllowed.body()).get("code").asString());
            }
            assertEquals(current, get(app, path));
            assertFalse(get(app, "/api/public/owner/meeting-types/Manual/slots").get("items").toString()
                    .contains("2026-11-02T09:00:00+03:00"));
        }
    }

    @Test
    void rejectionOneNanosecondBeforeStartIsAllowedWithoutReasonAndNeverExpiresLater() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        try (var app = start(clock)) {
            var saved = book(app, "Manual", true, "2026-11-02T09:00:00+03:00");
            clock.set("2026-11-02T05:59:59.999999999Z");
            var response = decide(app, saved.result().get("booking"), "reject", "last-moment");
            assertEquals(200, response.statusCode(), response.body());
            var rejected = json.readTree(response.body());
            assertEquals("REJECTED", rejected.get("status").asString());
            assertFalse(rejected.hasNonNull("rejectionReason"));
            clock.set("2026-11-02T06:00:00Z");
            assertEquals(rejected, get(app, "/api/owner/bookings/" + rejected.get("id").asString()));
            assertEquals(rejected, get(app, "/api/owner/operations/last-moment").get("booking"));
        }
    }

    @Test
    void exactStartExpiresBeforeEitherDecisionAndCannotBeRevivedWithFreshVersion() throws Exception {
        for (var action : List.of("confirm", "reject")) {
            var clock = new BookingSubmissionHttpTests.MutableClock();
            try (var app = start(clock)) {
                var saved = book(app, "Manual", true, "2026-11-02T09:00:00+03:00");
                var booking = saved.result().get("booking");
                clock.set("2026-11-02T06:00:00Z");
                var response = decide(app, booking, action, "boundary-" + action);
                assertEquals(409, response.statusCode(), response.body());
                var error = json.readTree(response.body());
                assertEquals("RESOURCE_CHANGED", error.get("code").asString());
                var expired = error.get("currentBooking");
                assertEquals("REJECTED", expired.get("status").asString());
                assertEquals("APPROVAL_EXPIRED", expired.get("rejectionReason").asString());
                assertEquals(2, expired.get("version").asInt());
                assertEquals(expired, get(app, "/api/owner/bookings/" + booking.get("id").asString()));
                var fresh = decide(app, expired, action, "fresh-" + action);
                assertEquals(409, fresh.statusCode());
                assertEquals("ACTION_NOT_ALLOWED", json.readTree(fresh.body()).get("code").asString());
                clock.set("2026-11-02T05:59:59Z");
                assertEquals(expired, get(app, "/api/owner/bookings/" + booking.get("id").asString()));
                var replay = decide(app, booking, action, "boundary-" + action);
                assertEquals(error, json.readTree(replay.body()));
                assertEquals(error, get(app, "/api/owner/operations/boundary-" + action).get("error"));
                assertEquals(expired, json.readTree(submit(app, saved.attempt()).body()).get("booking"));
            }
        }
    }

    @Test
    void genuinelyConcurrentOppositeDecisionsCommitExactlyOnceAndRetainBothOutcomes() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var saved = book(app, "Manual", true, "2026-11-02T09:00:00+03:00");
            var booking = saved.result().get("booking");
            var ready = new java.util.concurrent.CountDownLatch(2);
            var go = new java.util.concurrent.CountDownLatch(1);
            try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var tasks = List.of("confirm", "reject").stream().map(action -> executor.submit(() -> {
                    ready.countDown();
                    if (!go.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent decision did not start");
                    }
                    return decide(app, booking, action, "race-" + action);
                })).toList();
                assertEquals(true, ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
                go.countDown();
                var responses = List.of(tasks.get(0).get(), tasks.get(1).get());
                assertEquals(List.of(200, 409), responses.stream().map(HttpResponse::statusCode).sorted().toList());
                var winner = responses.stream().filter(response -> response.statusCode() == 200).findFirst()
                        .orElseThrow();
                var current = json.readTree(winner.body());
                assertEquals(2, current.get("version").asInt());
                var loser = json.readTree(responses.stream().filter(response -> response.statusCode() == 409)
                        .findFirst().orElseThrow().body());
                assertEquals("RESOURCE_CHANGED", loser.get("code").asString());
                assertEquals(current, loser.get("currentBooking"));
                for (int i = 0; i < 2; i++) {
                    var action = List.of("confirm", "reject").get(i);
                    var replay = decide(app, booking, action, "race-" + action);
                    assertEquals(responses.get(i).statusCode(), replay.statusCode());
                    assertEquals(json.readTree(responses.get(i).body()), json.readTree(replay.body()));
                    var operation = get(app, "/api/owner/operations/race-" + action);
                    assertEquals(replay.statusCode(), operation.get("statusCode").asInt());
                    assertEquals(json.readTree(replay.body()), operation.get(replay.statusCode() == 200
                            ? "booking" : "error"));
                }
                assertEquals(current, get(app, "/api/owner/bookings/" + booking.get("id").asString()));
                assertEquals(current, json.readTree(submit(app, saved.attempt()).body()).get("booking"));
                var slotPresent = get(app, "/api/public/owner/meeting-types/Manual/slots").get("items").toString()
                        .contains("2026-11-02T09:00:00+03:00");
                assertEquals(current.get("status").asString().equals("REJECTED"), slotPresent);
            }
        }
    }

    @Test
    void rejectionReleasesOnlyItsOccupancyAndAllViewsAndAttemptShowCurrentTerminalBooking() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var saved = book(app, "Manual", true, "2026-11-02T09:00:00+03:00");
            var booking = saved.result().get("booking");
            assertFalse(get(app, "/api/public/owner/meeting-types/Manual/slots").get("items").toString()
                    .contains("2026-11-02T09:00:00+03:00"));
            var response = decide(app, booking, "reject", "reject-key");
            assertEquals(200, response.statusCode(), response.body());
            var rejected = json.readTree(response.body());
            var expected = booking.deepCopy();
            ((tools.jackson.databind.node.ObjectNode) expected).put("status", "REJECTED").put("version", 2);
            assertEquals(expected, rejected);
            assertEquals(0, get(app, "/api/owner/bookings?view=upcoming").get("items").size());
            assertEquals(rejected, get(app, "/api/owner/bookings?view=history").get("items").get(0));
            assertEquals(rejected, get(app,
                    "/api/owner/calendar?from=2026-11-02T06:00:00Z&to=2026-11-02T07:00:00Z")
                    .get("items").get(0));
            assertEquals(rejected, get(app, "/api" + saved.result().get("resultUrl").asString()));
            var replay = submit(app, saved.attempt());
            assertEquals(200, replay.statusCode());
            assertEquals(rejected, json.readTree(replay.body()).get("booking"));
            assertEquals(rejected, get(app, "/api/owner/operations/reject-key").get("booking"));
            assertEquals(rejected, json.readTree(decide(app, booking, "reject", "reject-key").body()));
            assertEquals(true, get(app, "/api/public/owner/meeting-types/Manual/slots").get("items").toString()
                    .contains("2026-11-02T09:00:00+03:00"));
            var replacement = book(app, "Other", false, "2026-11-02T09:00:00+03:00");
            assertEquals("CONFIRMED", replacement.result().get("booking").get("status").asString());
            assertFalse(get(app, "/api/public/owner/meeting-types/Manual/slots").get("items").toString()
                    .contains("2026-11-02T09:00:00+03:00"));
        }
    }

    @Test
    void confirmationKeepsSnapshotAndOccupancyAndGuestAttemptReplaysCurrentBooking() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        try (var app = start(clock)) {
            var saved = book(app, "Manual", true, "2026-11-02T09:00:00+03:00");
            var booking = saved.result().get("booking");
            // Owner management is not restricted by the guest's tomorrow-only window or public switch.
            clock.set("2026-11-02T05:59:59.999999999Z");
            assertEquals(200, send(app, "PUT", "/api/owner/public-booking",
                    "{\"enabled\":false,\"expectedVersion\":1}", UUID.randomUUID().toString()).statusCode());
            var response = decide(app, booking, "confirm", "confirm-key");
            assertEquals(200, response.statusCode(), response.body());
            var confirmed = json.readTree(response.body());
            assertEquals("CONFIRMED", confirmed.get("status").asString());
            assertEquals(2, confirmed.get("version").asInt());
            var expected = booking.deepCopy();
            ((tools.jackson.databind.node.ObjectNode) expected).put("status", "CONFIRMED").put("version", 2);
            assertEquals(expected, confirmed);
            assertEquals(confirmed, get(app, "/api" + saved.result().get("resultUrl").asString()));
            var replay = submit(app, saved.attempt());
            assertEquals(200, replay.statusCode());
            assertEquals(confirmed, json.readTree(replay.body()).get("booking"));
            assertEquals(confirmed, get(app, "/api/owner/bookings?view=upcoming").get("items").get(0));
            assertEquals(confirmed, get(app,
                    "/api/owner/calendar?from=2026-11-02T06:00:00Z&to=2026-11-02T07:00:00Z")
                    .get("items").get(0));
            assertEquals(confirmed, get(app, "/api/owner/operations/confirm-key").get("booking"));
            assertEquals(confirmed, json.readTree(decide(app, booking, "confirm", "confirm-key").body()));
            clock.set("2026-11-02T06:00:00Z");
            assertEquals(confirmed, get(app, "/api/owner/bookings/" + booking.get("id").asString()));
        }
    }

    private ConfigurableApplicationContext start(BookingSubmissionHttpTests.MutableClock clock) {
        return new SpringApplicationBuilder(CalendarApplication.class)
                .initializers(context -> context.getBeanFactory().registerSingleton("testClock", clock))
                .run("--server.port=0", "--calendar.initial.booking-enabled=true");
    }

    private record SavedBooking(JsonNode attempt, JsonNode result) { }

    private SavedBooking book(ConfigurableApplicationContext app, String id, boolean approval, String start)
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
        return new SavedBooking(attempt, json.readTree(submitted.body()));
    }

    private HttpResponse<String> submit(ConfigurableApplicationContext app, JsonNode attempt) throws Exception {
        return send(app, "POST", "/api/booking-attempts/" + attempt.get("attemptId").asString() + "/submit",
                null, null, "Bearer " + attempt.get("attemptSecret").asString());
    }

    private HttpResponse<String> decide(ConfigurableApplicationContext app, JsonNode booking, String action, String key)
            throws Exception {
        return send(app, "POST", "/api/owner/bookings/" + booking.get("id").asString() + "/" + action,
                "{\"expectedVersion\":" + booking.get("version").asInt() + "}", key);
    }

    private JsonNode get(ConfigurableApplicationContext app, String path) throws Exception {
        var response = send(app, "GET", path, null, null);
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
