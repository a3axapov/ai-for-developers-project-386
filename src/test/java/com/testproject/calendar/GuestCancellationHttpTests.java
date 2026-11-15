package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class GuestCancellationHttpTests {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void guestMalformedCommandsNeverEnterOwnerOperationJournal() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var saved = book(app, false);
            assertError(sendWithKey(app, "POST", saved.path() + "/cancel", "{", null, "guest-not-owner"),
                    400, "MALFORMED_REQUEST");
            var operation = send(app, "GET", "/api/owner/operations/guest-not-owner", null, null);
            assertEquals(410, operation.statusCode(), operation.body());
            assertEquals("OPERATION_UNAVAILABLE", json.readTree(operation.body()).get("code").asString());
            assertEquals(saved.booking(), get(app, saved.path()));
        }
    }

    @Test
    void advertisedGuestActionsTrackStatusAndExactStartIncludingExport() throws Exception {
        for (boolean manual : List.of(true, false)) {
            var clock = new BookingSubmissionHttpTests.MutableClock();
            try (var app = start(clock)) {
                var saved = book(app, manual);
                assertEquals(json.readTree("[\"cancel\",\"export\"]"), get(app, saved.path()).get("allowedActions"));
                clock.set("2026-11-02T05:59:59.999999999Z");
                assertEquals(json.readTree("[\"cancel\",\"export\"]"), get(app, saved.path()).get("allowedActions"));
                clock.set("2026-11-02T06:00:00Z");
                assertEquals(json.readTree(manual ? "[]" : "[\"export\"]"),
                        get(app, saved.path()).get("allowedActions"));
            }
        }
    }

    @Test
    void ownerConfirmationRequiresNewGuestConsentAndRejectedOrStartedCannotBeCancelled() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        try (var app = start(clock)) {
            var saved = book(app, true);
            var ownerPath = "/api/owner/bookings/" + saved.booking().get("id").asString();
            var confirmation = send(app, "POST", ownerPath + "/confirm", "{\"expectedVersion\":1}", null);
            assertEquals(200, confirmation.statusCode(), confirmation.body());
            var current = get(app, saved.path());
            var stale = send(app, "POST", saved.path() + "/cancel", "{\"expectedVersion\":1}", null);
            assertError(stale, 409, "RESOURCE_CHANGED");
            assertEquals(current, json.readTree(stale.body()).get("currentBooking"));
            assertEquals(current, get(app, saved.path()));
            assertEquals(1, get(app, "/api/owner/bookings?view=upcoming").get("items").size());
            assertEquals(200, send(app, "POST", saved.path() + "/cancel",
                    "{\"expectedVersion\":2}", null).statusCode());
        }
        for (boolean manual : List.of(true, false)) {
            clock = new BookingSubmissionHttpTests.MutableClock();
            try (var app = start(clock)) {
                var saved = book(app, manual);
                clock.set("2026-11-02T06:00:00Z");
                var current = get(app, saved.path());
                var response = send(app, "POST", saved.path() + "/cancel",
                        "{\"expectedVersion\":" + current.get("version").asInt() + "}", null);
                assertError(response, 409, "ACTION_NOT_ALLOWED");
                assertEquals(current, json.readTree(response.body()).get("currentBooking"));
                assertEquals(current, get(app, saved.path()));
                assertEquals(manual ? "REJECTED" : "CONFIRMED", current.get("status").asString());
            }
        }
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var saved = book(app, true);
            assertEquals(200, send(app, "POST",
                    "/api/owner/bookings/" + saved.booking().get("id").asString() + "/reject",
                    "{\"expectedVersion\":1}", null).statusCode());
            var rejected = get(app, saved.path());
            assertError(send(app, "POST", saved.path() + "/cancel", "{\"expectedVersion\":2}", null),
                    409, "ACTION_NOT_ALLOWED");
            assertEquals(rejected, get(app, saved.path()));
        }
    }

    @Test
    void invalidVersionsAndUnknownLinksAreNeutralWithoutPartialChanges() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var saved = book(app, false);
            for (String body : List.of("{}", "{\"expectedVersion\":0}", "{\"expectedVersion\":-1}")) {
                var response = send(app, "POST", saved.path() + "/cancel", body, null);
                assertError(response, 422, "VALIDATION_FAILED");
                assertEquals("expectedVersion", json.readTree(response.body()).get("fieldErrors").get(0)
                        .get("field").asString());
                assertEquals(saved.booking(), get(app, saved.path()));
            }
            for (String body : List.of("{", "{\"expectedVersion\":1.5}", "{\"expectedVersion\":\"1\"}", "")) {
                assertError(send(app, "POST", saved.path() + "/cancel", body, null), 400, "MALFORMED_REQUEST");
                assertEquals(saved.booking(), get(app, saved.path()));
            }
            var unknown = send(app, "POST", "/api/bookings/unknown/cancel", "{\"expectedVersion\":1}", null);
            assertError(unknown, 404, "BOOKING_NOT_FOUND");
            assertEquals(json.readTree("{\"code\":\"BOOKING_NOT_FOUND\",\"message\":\"Бронирование не найдено\"}"),
                    json.readTree(unknown.body()));
        }
    }

    @Test
    void cancellationJustBeforeStartReleasesOnlyOwnOccupancyDespiteDisabledBookingAndArchivedType() throws Exception {
        for (boolean manual : List.of(true, false)) {
            var clock = new BookingSubmissionHttpTests.MutableClock();
            try (var app = start(clock)) {
                var saved = book(app, manual);
                var other = book(app, false, "Other", "2026-11-02T09:30:00+03:00");
                assertEquals(200, send(app, "POST", "/api/owner/meeting-types/Meeting/archive",
                        "{\"expectedVersion\":1}", null).statusCode());
                assertEquals(200, send(app, "PUT", "/api/owner/public-booking",
                        "{\"enabled\":false,\"expectedVersion\":1}", null).statusCode());
                clock.set("2026-11-02T05:59:59.999999999Z");
                var cancelled = send(app, "POST", saved.path() + "/cancel", "{\"expectedVersion\":1}", null);
                assertEquals(200, cancelled.statusCode(), cancelled.body());
                assertEquals(other.booking(), get(app, other.path()));
                assertEquals(1, get(app, "/api/owner/bookings?view=upcoming").get("items").size());
                clock.set("2026-11-02T06:00:00Z");
                assertEquals(json.readTree(cancelled.body()), get(app, saved.path()));
                assertEquals(200, send(app, "PUT", "/api/owner/public-booking",
                        "{\"enabled\":true,\"expectedVersion\":2}", null).statusCode());
                // Return the test clock to the guest window: the other type sees only the released interval.
                clock.set("2026-11-01T00:00:00Z");
                var slots = get(app, "/api/public/owner/meeting-types/Other/slots").get("items");
                var starts = java.util.stream.StreamSupport.stream(slots.spliterator(), false)
                        .map(slot -> slot.get("startsAt").asString()).toList();
                assertTrue(starts.contains("2026-11-02T09:00:00+03:00"));
                org.junit.jupiter.api.Assertions.assertFalse(starts.contains("2026-11-02T09:30:00+03:00"));
            }
        }
    }

    @Test
    void trulyConcurrentGuestAndOwnerCommandsHaveOneAtomicWinner() throws Exception {
        for (String action : List.of("confirm", "reject", "cancel", "guest")) {
            try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
                var saved = book(app, !action.equals("cancel"));
                var ownerPath = "/api/owner/bookings/" + saved.booking().get("id").asString() + "/" + action;
                var ready = new java.util.concurrent.CountDownLatch(2);
                var go = new java.util.concurrent.CountDownLatch(1);
                try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                    var guest = pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return send(app, "POST", saved.path() + "/cancel", "{\"expectedVersion\":1}", null);
                    });
                    var other = pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return send(app, "POST", action.equals("guest") ? saved.path() + "/cancel" : ownerPath,
                                "{\"expectedVersion\":1}", null);
                    });
                    assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
                    go.countDown();
                    var a = guest.get(20, java.util.concurrent.TimeUnit.SECONDS);
                    var b = other.get(20, java.util.concurrent.TimeUnit.SECONDS);
                    assertTrue(a.statusCode() == 200 || b.statusCode() == 200);
                    if (List.of("confirm", "reject").contains(action)) {
                        assertEquals(List.of(200, 409), java.util.stream.Stream.of(a.statusCode(), b.statusCode())
                                .sorted().toList());
                    } else if (action.equals("guest")) {
                        assertEquals(200, a.statusCode());
                        assertEquals(200, b.statusCode());
                    } else {
                        assertEquals(200, a.statusCode());
                        assertTrue(b.statusCode() == 200 || b.statusCode() == 409);
                    }
                    var current = get(app, saved.path());
                    assertEquals(2, current.get("version").asInt());
                    assertEquals(saved.booking().get("startsAt"), current.get("startsAt"));
                    assertEquals(saved.booking().get("comment"), current.get("comment"));
                    assertEquals(current, get(app, "/api/owner/bookings/" + current.get("id").asString()));
                    if (a.statusCode() == 409) {
                        assertEquals(current, json.readTree(a.body()).get("currentBooking"));
                    }
                }
            }
        }
    }

    private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(code, json.readTree(response.body()).get("code").asString());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("no-referrer", response.headers().firstValue("Referrer-Policy").orElseThrow());
    }

    @Test
    void guestWithdrawsPendingAndCancelsConfirmedPreservingDetailsAndSafeAttemptReplay() throws Exception {
        for (boolean manual : List.of(true, false)) {
            try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
                var saved = book(app, manual);
                var before = get(app, saved.path());
                assertEquals(saved.booking(), before);
                var response = send(app, "POST", saved.path() + "/cancel", "{\"expectedVersion\":1}", null);
                assertEquals(200, response.statusCode(), response.body());
                var cancelled = json.readTree(response.body());
                assertEquals("CANCELLED", cancelled.get("status").asString());
                assertEquals(2, cancelled.get("version").asInt());
                assertEquals(0, cancelled.get("allowedActions").size());
                for (String field : List.of("id", "title", "description", "durationMinutes", "requiresApproval",
                        "startsAt", "endsAt", "guestName", "guestEmail", "comment")) {
                    assertEquals(before.get(field), cancelled.get(field), field);
                }
                assertEquals(cancelled, get(app, saved.path()));
                assertEquals(cancelled, get(app, "/api/owner/bookings?view=history").get("items").get(0));
                var replay = send(app, "POST", saved.submitPath(), null, saved.authorization());
                assertEquals(200, replay.statusCode());
                assertEquals(cancelled, json.readTree(replay.body()).get("booking"));
                assertEquals(cancelled, json.readTree(send(app, "POST", saved.path() + "/cancel",
                        "{\"expectedVersion\":1}", null).body()));
                assertEquals(2, get(app, saved.path()).get("version").asInt());
                assertTrue(get(app, "/api/public/owner/meeting-types/Meeting/slots").get("items").toString()
                        .contains("2026-11-02T09:00:00+03:00"));
            }
        }
    }

    private ConfigurableApplicationContext start(BookingSubmissionHttpTests.MutableClock clock) {
        return new SpringApplicationBuilder(CalendarApplication.class)
                .initializers(context -> context.getBeanFactory().registerSingleton("testClock", clock))
                .run("--server.port=0", "--calendar.initial.booking-enabled=true");
    }

    private record Saved(JsonNode booking, String path, String submitPath, String authorization) { }

    private Saved book(ConfigurableApplicationContext app, boolean manual) throws Exception {
        return book(app, manual, "Meeting", "2026-11-02T09:00:00+03:00");
    }

    private Saved book(ConfigurableApplicationContext app, boolean manual, String id, String startsAt)
            throws Exception {
        assertEquals(201, send(app, "POST", "/api/owner/meeting-types", json.writeValueAsString(Map.of(
                "id", id, "title", "Консультация " + id, "description", "Сохранённое подробное описание встречи",
                "durationMinutes", 30, "requiresApproval", manual)), null).statusCode());
        var slots = get(app, "/api/public/owner/meeting-types/" + id + "/slots");
        var prepared = send(app, "POST", "/api/public/owner/booking-attempts", json.writeValueAsString(Map.of(
                "meetingTypeId", id, "conditionsToken", slots.get("conditionsToken").asString(),
                "startsAt", startsAt, "guestName", "Анна 李", "guestEmail", "guest@example.com",
                "comment", "Сохранённый\nкомментарий")), null);
        assertEquals(201, prepared.statusCode(), prepared.body());
        var attempt = json.readTree(prepared.body());
        var submit = "/api/booking-attempts/" + attempt.get("attemptId").asString() + "/submit";
        var authorization = "Bearer " + attempt.get("attemptSecret").asString();
        var response = send(app, "POST", submit, null, authorization);
        assertEquals(201, response.statusCode(), response.body());
        var saved = json.readTree(response.body());
        return new Saved(saved.get("booking"), "/api" + saved.get("resultUrl").asString(), submit, authorization);
    }

    private JsonNode get(ConfigurableApplicationContext app, String path) throws Exception {
        var response = send(app, "GET", path, null, null);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        return json.readTree(response.body());
    }

    private HttpResponse<String> send(ConfigurableApplicationContext app, String method, String path, String body,
            String authorization) throws Exception {
        return sendWithKey(app, method, path, body, authorization, null);
    }

    private HttpResponse<String> sendWithKey(ConfigurableApplicationContext app, String method, String path,
            String body, String authorization, String key) throws Exception {
        int port = ((WebServerApplicationContext) app).getWebServer().getPort();
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json");
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        if (path.startsWith("/api/owner/") && !method.equals("GET")) {
            builder.header("Idempotency-Key", UUID.randomUUID().toString());
        } else if (key != null) {
            builder.header("Idempotency-Key", key);
        }
        try (var client = HttpClient.newHttpClient()) {
            return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
