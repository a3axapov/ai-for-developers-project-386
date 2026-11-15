package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

class OwnerRescheduleHttpTests {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void movesSavedBookingBeyondGuestWindowWithPublicBookingOffAndRetainsLinkAndAttempt() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var saved = book(app, "Auto", false, "2026-11-02T09:00:00+03:00");
            var booking = saved.get("booking");
            assertEquals(200, send(app, "PUT", "/api/owner/meeting-types/Auto",
                    "{\"title\":\"Изменённые условия\",\"description\":\"Совершенно новое подробное описание\","
                    + "\"durationMinutes\":60,\"requiresApproval\":true,\"expectedVersion\":1}", "edit").statusCode());
            assertEquals(200, send(app, "PUT", "/api/owner/public-booking",
                    "{\"enabled\":false,\"expectedVersion\":1}", "off").statusCode());
            var slots = get(app, path(booking) + "/reschedule-slots?fromDate=2027-01-04&toDate=2027-01-05");
            assertEquals("Europe/Moscow", slots.get("ownerTimeZone").asString());
            assertTrue(slots.get("items").valueStream().anyMatch(slot ->
                    slot.get("startsAt").asString().equals("2027-01-04T17:30:00+03:00")
                    && slot.get("endsAt").asString().equals("2027-01-04T18:00:00+03:00")));
            var response = move(app, booking, "2027-01-04T17:30:00+03:00", "move");
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var moved = json.readTree(response.body());
            var expected = booking.deepCopy();
            ((tools.jackson.databind.node.ObjectNode) expected).put("startsAt", "2027-01-04T17:30:00+03:00")
                    .put("endsAt", "2027-01-04T18:00:00+03:00").put("version", 2);
            assertEquals(expected, moved);
            assertEquals(moved, get(app, path(booking)));
            assertEquals(moved, get(app, "/api" + saved.get("resultUrl").asString()));
            assertEquals(moved, get(app, "/api/owner/bookings?view=upcoming").get("items").get(0));
            assertEquals(moved, get(app,
                    "/api/owner/calendar?from=2027-01-04T00:00:00Z&to=2027-01-05T00:00:00Z").get("items").get(0));
            assertEquals(0, get(app,
                    "/api/owner/calendar?from=2026-11-02T00:00:00Z&to=2026-11-03T00:00:00Z").get("items").size());
            assertEquals(moved, json.readTree(submit(app, saved.get("attempt")).body()).get("booking"));
            assertEquals(moved, json.readTree(move(app, booking, "2027-01-04T17:30:00+03:00", "move").body()));
        }
    }

    private ConfigurableApplicationContext start(BookingSubmissionHttpTests.MutableClock clock) {
        return new SpringApplicationBuilder(CalendarApplication.class)
                .initializers(context -> context.getBeanFactory().registerSingleton("testClock", clock))
                .run("--server.port=0", "--calendar.initial.booking-enabled=true");
    }

    @Test
    void malformedCommandsHaveDistinctImmutableOutcomesAndDoNotChangeBooking() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var booking = book(app, "Auto", false, "2026-11-02T09:00:00+03:00").get("booking");
            var body = "{\"startsAt\":\"bad\",\"expectedVersion\":1}";
            var bad = send(app, "POST", path(booking) + "/reschedule", body, "bad");
            assertEquals(400, bad.statusCode(), bad.body());
            assertEquals(json.readTree(bad.body()), get(app, "/api/owner/operations/bad").get("error"));
            var different = send(app, "POST", path(booking) + "/reschedule", "{broken", "bad");
            assertEquals(409, different.statusCode(), different.body());
            assertEquals("IDEMPOTENCY_KEY_REUSED", json.readTree(different.body()).get("code").asString());
            assertEquals(booking, get(app, path(booking)));
        }
    }

    private String path(JsonNode booking) {
        return "/api/owner/bookings/" + booking.get("id").asString();
    }

    @Test
    void pendingMoveUsesNewStartForExpiryAndGuestStaleConsentEvenAfterOldStart() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        try (var app = start(clock)) {
            var saved = book(app, "Manual", true, "2026-11-02T09:00:00+03:00");
            var booking = saved.get("booking");
            var moved = json.readTree(move(app, booking, "2026-11-03T10:00:00+03:00", "move").body());
            assertEquals("PENDING", moved.get("status").asString());
            clock.set("2026-11-02T06:00:00Z");
            assertEquals(moved, get(app, "/api" + saved.get("resultUrl").asString()));
            var stale = send(app, "POST", "/api" + saved.get("resultUrl").asString() + "/cancel",
                    "{\"expectedVersion\":1}", null);
            assertEquals(409, stale.statusCode());
            assertEquals("RESOURCE_CHANGED", json.readTree(stale.body()).get("code").asString());
            assertEquals(moved, json.readTree(stale.body()).get("currentBooking"));
            clock.set("2026-11-03T06:59:59.999999999Z");
            assertEquals("PENDING", get(app, path(booking)).get("status").asString());
            clock.set("2026-11-03T07:00:00Z");
            var expired = get(app, path(booking));
            assertEquals("REJECTED", expired.get("status").asString());
            assertEquals("APPROVAL_EXPIRED", expired.get("rejectionReason").asString());
            assertEquals(3, expired.get("version").asInt());
            assertEquals(moved, get(app, "/api/owner/operations/move").get("booking"));
            assertEquals(moved, json.readTree(move(app, booking, "2026-11-03T10:00:00+03:00", "move").body()));
            assertEquals(expired, json.readTree(submit(app, saved.get("attempt")).body()).get("booking"));
        }
    }

    @Test
    void movingPendingEarlierExpiresAtTheNewStartAndReleasesNewOccupancyNotAtOldStart() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        try (var app = start(clock)) {
            var saved = book(app, "Manual", true, "2026-11-03T09:00:00+03:00");
            var booking = saved.get("booking");
            var moved = move(app, booking, "2026-11-02T09:00:00+03:00", "earlier");
            assertEquals(200, moved.statusCode(), moved.body());
            clock.set("2026-11-02T06:00:00Z");
            var expired = get(app, "/api" + saved.get("resultUrl").asString());
            assertEquals("REJECTED", expired.get("status").asString());
            assertEquals("APPROVAL_EXPIRED", expired.get("rejectionReason").asString());
            assertEquals(3, expired.get("version").asInt());
            assertEquals(0, get(app, "/api/owner/bookings?view=upcoming").get("items").size());
            assertEquals(expired, get(app, "/api/owner/bookings?view=history").get("items").get(0));
            assertEquals(json.readTree(moved.body()), get(app, "/api/owner/operations/earlier").get("booking"));
        }
    }

    @Test
    void originalAndTargetStrictBoundariesTodayOwnOverlapAndEqualTimeRemainLegalWhenAvailable() throws Exception {
        for (boolean manual : List.of(false, true)) {
            var clock = new BookingSubmissionHttpTests.MutableClock();
            try (var app = start(clock)) {
                var saved = book(app, "Type", manual, "2026-11-02T09:00:00+03:00");
                var booking = saved.get("booking");
                clock.set("2026-11-02T05:59:59.999999999Z");
                var overlapping = move(app, booking, "2026-11-02T09:15:00+03:00", "overlap");
                assertEquals(200, overlapping.statusCode(), overlapping.body());
                var current = json.readTree(overlapping.body());
                var same = move(app, current, "2026-11-02T09:15:00+03:00", "same");
                assertEquals(200, same.statusCode(), same.body());
                current = json.readTree(same.body());
                clock.set("2026-11-02T06:00:00Z");
                var targetNow = move(app, current, "2026-11-02T09:00:00+03:00", "now");
                assertEquals(409, targetNow.statusCode());
                assertEquals("SLOT_UNAVAILABLE", json.readTree(targetNow.body()).get("code").asString());
                assertEquals(current, get(app, path(booking)));
                clock.set("2026-11-02T06:15:00Z");
                var sourceNow = move(app, current, "2026-11-02T10:00:00+03:00", "source-now");
                assertEquals(409, sourceNow.statusCode());
                assertEquals(manual ? "RESOURCE_CHANGED" : "ACTION_NOT_ALLOWED",
                        json.readTree(sourceNow.body()).get("code").asString());
                assertEquals(manual ? "REJECTED" : "CONFIRMED", get(app, path(booking)).get("status").asString());
            }
        }
    }

    @Test
    void availabilityFailuresLeaveEveryFieldAndOccupancyUnchangedAndFailedOutcomesStayImmutable() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var saved = book(app, "Auto", false, "2026-11-02T09:00:00+03:00");
            var booking = saved.get("booking");
            book(app, "Other", false, "2026-11-02T10:00:00+03:00");
            assertEquals(201, send(app, "POST", "/api/owner/time-blocks",
                    "{\"title\":\"Занято\",\"startsAt\":\"2026-11-03T09:00:00+03:00\","
                    + "\"endsAt\":\"2026-11-03T10:00:00+03:00\"}", "block").statusCode());
            assertEquals(201, send(app, "POST", "/api/owner/out-of-office",
                    "{\"startDate\":\"2026-11-04\",\"endDate\":\"2026-11-04\"}", "vacation").statusCode());
            assertEquals(200, send(app, "PUT", "/api/owner/meeting-types/Auto/date-overrides/2026-11-05",
                    "{\"intervals\":[],\"expectedVersion\":1}", "closed").statusCode());
            for (var start : List.of("2026-11-02T10:00:00+03:00", "2026-11-03T09:00:00+03:00",
                    "2026-11-04T09:00:00+03:00", "2026-11-05T09:00:00+03:00",
                    "2026-11-02T08:45:00+03:00", "2026-11-02T09:01:00+03:00",
                    "2026-11-02T17:45:00+03:00", "2026-11-01T09:00:00+03:00")) {
                var key = UUID.randomUUID().toString();
                var response = move(app, booking, start, key);
                assertEquals(409, response.statusCode(), response.body());
                assertEquals(booking, get(app, path(booking)));
                assertEquals(booking, get(app, "/api" + saved.get("resultUrl").asString()));
                var before = get(app, "/api/public/owner/meeting-types/Auto/slots");
                assertTrue(before.get("items").valueStream().noneMatch(slot ->
                        slot.get("startsAt").asString().equals("2026-11-02T09:00:00+03:00")));
                assertEquals(json.readTree(response.body()), get(app, "/api/owner/operations/" + key).get("error"));
            }
            assertEquals(200, send(app, "POST", "/api/owner/meeting-types/Auto/archive",
                    "{\"expectedVersion\":2}", "archive").statusCode());
            var archived = move(app, booking, "2026-11-06T09:00:00+03:00", "archived");
            assertEquals(409, archived.statusCode());
            assertEquals("ACTION_NOT_ALLOWED", json.readTree(archived.body()).get("code").asString());
            var slots = send(app, "GET", path(booking) + "/reschedule-slots?fromDate=2026-11-06&toDate=2026-11-07",
                    null, null);
            assertEquals(409, slots.statusCode());
            assertEquals(booking, get(app, path(booking)));
        }
    }

    @Test
    void staleVersionsTerminalStatesValidationAndUnknownResourcesNeverApplyMove() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var booking = book(app, "Auto", false, "2026-11-02T09:00:00+03:00").get("booking");
            for (var body : List.of("{}", "{\"expectedVersion\":0}", "{\"startsAt\":null,\"expectedVersion\":1}")) {
                var response = send(app, "POST", path(booking) + "/reschedule", body, UUID.randomUUID().toString());
                assertEquals(422, response.statusCode(), response.body());
                assertEquals("VALIDATION_FAILED", json.readTree(response.body()).get("code").asString());
                assertEquals(booking, get(app, path(booking)));
            }
            var stale = send(app, "POST", path(booking) + "/reschedule",
                    "{\"expectedVersion\":2,\"startsAt\":\"2026-11-03T09:00:00+03:00\"}", "stale");
            assertEquals(409, stale.statusCode());
            assertEquals(booking, json.readTree(stale.body()).get("currentBooking"));
            assertEquals("RESOURCE_CHANGED", json.readTree(stale.body()).get("code").asString());
            assertEquals(422, send(app, "GET", path(booking)
                    + "/reschedule-slots?fromDate=bad&toDate=2026-11-02", null, null).statusCode());
            assertEquals(422, send(app, "GET", path(booking)
                    + "/reschedule-slots?fromDate=2026-11-03&toDate=2026-11-02", null, null).statusCode());
            assertEquals(400, send(app, "GET", path(booking) + "/reschedule-slots", null, null).statusCode());
            assertEquals(404, send(app, "POST", "/api/owner/bookings/missing/reschedule",
                    "{\"expectedVersion\":1,\"startsAt\":\"2026-11-03T09:00:00+03:00\"}", "missing").statusCode());
            assertEquals(200, send(app, "POST", path(booking) + "/cancel", "{\"expectedVersion\":1}", "cancel")
                    .statusCode());
            var terminal = get(app, path(booking));
            var refused = move(app, terminal, "2026-11-03T09:00:00+03:00", "terminal");
            assertEquals(409, refused.statusCode());
            assertEquals("ACTION_NOT_ALLOWED", json.readTree(refused.body()).get("code").asString());
            assertEquals(terminal, get(app, path(booking)));
            assertEquals(json.readTree(stale.body()), get(app, "/api/owner/operations/stale").get("error"));
        }
    }

    @Test
    void genuinelyConcurrentAutomaticBookingAndRescheduleCannotOverlap() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var booking = book(app, "Auto", false, "2026-11-02T09:00:00+03:00").get("booking");
            var prepared = send(app, "POST", "/api/public/owner/booking-attempts", json.writeValueAsString(Map.of(
                    "meetingTypeId", "Auto", "conditionsToken",
                    get(app, "/api/public/owner/meeting-types/Auto/slots").get("conditionsToken").asString(),
                    "startsAt", "2026-11-02T10:00:00+03:00", "guestName", "Другой гость",
                    "guestEmail", "other@example.com")), null);
            assertEquals(201, prepared.statusCode());
            var attempt = json.readTree(prepared.body());
            var responses = race(() -> move(app, booking, "2026-11-02T10:00:00+03:00", "move"),
                    () -> submit(app, attempt));
            assertEquals(1, responses.stream().filter(r -> r.statusCode() < 300).count());
            assertEquals(1, responses.stream().filter(r -> r.statusCode() == 409).count());
            var current = get(app, path(booking));
            assertEquals(responses.get(0).statusCode() == 200 ? "2026-11-02T10:00:00+03:00"
                    : "2026-11-02T09:00:00+03:00", current.get("startsAt").asString());
            assertNoOverlaps(get(app, "/api/owner/bookings?view=upcoming").get("items"));
        }
    }

    @Test
    void racesWithBlockGuestCancelOtherMoveConfirmAndArchiveAreSerializedWithoutPartialEffects() throws Exception {
        for (var rival : List.of("block", "guest-cancel", "reschedule", "confirm", "archive")) {
            try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
                boolean pending = rival.equals("confirm");
                var saved = book(app, "Type", pending, "2026-11-02T09:00:00+03:00");
                var booking = saved.get("booking");
                var responses = race(() -> move(app, booking, "2026-11-02T10:00:00+03:00", "move"), () -> {
                    return switch (rival) {
                        case "block" -> send(app, "POST", "/api/owner/time-blocks",
                                "{\"title\":\"Занято\",\"startsAt\":\"2026-11-02T10:00:00+03:00\","
                                + "\"endsAt\":\"2026-11-02T10:30:00+03:00\"}", "block");
                        case "guest-cancel" -> send(app, "POST", "/api" + saved.get("resultUrl").asString() + "/cancel",
                                "{\"expectedVersion\":1}", null);
                        case "reschedule" -> move(app, booking, "2026-11-02T11:00:00+03:00", "other-move");
                        case "confirm" -> send(app, "POST", path(booking) + "/confirm",
                                "{\"expectedVersion\":1}", "confirm");
                        default -> send(app, "POST", "/api/owner/meeting-types/Type/archive",
                                "{\"expectedVersion\":1}", "archive");
                    };
                });
                assertTrue(responses.stream().allMatch(r -> r.statusCode() == 200 || r.statusCode() == 201
                        || r.statusCode() == 409), responses.toString());
                if (!rival.equals("archive")) {
                    assertEquals(1, responses.stream().filter(r -> r.statusCode() < 300).count());
                }
                var current = get(app, path(booking));
                assertEquals(current, get(app, "/api" + saved.get("resultUrl").asString()));
                if (responses.get(0).statusCode() == 200) {
                    assertEquals("2026-11-02T10:00:00+03:00", current.get("startsAt").asString());
                } else {
                    assertEquals(rival.equals("reschedule") ? "2026-11-02T11:00:00+03:00"
                            : "2026-11-02T09:00:00+03:00", current.get("startsAt").asString());
                }
                assertEquals(responses.get(0).statusCode() == 200 || !List.of("block", "archive").contains(rival)
                        ? 2 : 1, current.get("version").asInt());
                assertNoOverlaps(get(app, "/api/owner/bookings?view=upcoming").get("items"));
                if (rival.equals("block") && responses.get(0).statusCode() == 200) {
                    assertEquals(0, get(app, "/api/owner/calendar?from=2026-11-02T00:00:00Z&to=2026-11-03T00:00:00Z")
                            .get("timeBlocks").size());
                }
            }
        }
    }

    private List<HttpResponse<String>> race(java.util.concurrent.Callable<HttpResponse<String>> first,
            java.util.concurrent.Callable<HttpResponse<String>> second) throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(2);
        var go = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks = List.of(first, second).stream().map(task -> executor.submit(() -> {
                ready.countDown();
                if (!go.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent requests did not start");
                }
                return task.call();
            })).toList();
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            go.countDown();
            return List.of(tasks.get(0).get(), tasks.get(1).get());
        }
    }

    private void assertNoOverlaps(JsonNode items) {
        var bookings = items.valueStream().toList();
        for (int i = 0; i < bookings.size(); i++) {
            for (int j = i + 1; j < bookings.size(); j++) {
                var a = bookings.get(i);
                var b = bookings.get(j);
                assertTrue(!java.time.OffsetDateTime.parse(a.get("startsAt").asString()).toInstant()
                        .isBefore(java.time.OffsetDateTime.parse(b.get("endsAt").asString()).toInstant())
                        || !java.time.OffsetDateTime.parse(b.get("startsAt").asString()).toInstant()
                        .isBefore(java.time.OffsetDateTime.parse(a.get("endsAt").asString()).toInstant()));
            }
        }
    }

    @Test
    void ownerSlotsHonorCurrentTypeZoneDstFoldsActualSavedDurationAndCrossMidnightAvailability() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        clock.set("2026-10-18T00:00:00Z");
        try (var app = start(clock)) {
            assertEquals(200, send(app, "PUT", "/api/owner/time-zone",
                    "{\"timeZone\":\"Europe/Berlin\",\"applyToActiveTypes\":false,\"expectedVersion\":1}", "zone")
                    .statusCode());
            var booking = book(app, "Type", false, "2026-10-19T09:00:00+02:00").get("booking");
            assertEquals(200, send(app, "PUT", "/api/owner/meeting-types/Type/date-overrides/2026-10-25",
                    "{\"intervals\":[{\"start\":\"02:00\",\"end\":\"02:30\"}],\"expectedVersion\":1}", "fold")
                    .statusCode());
            var slots = get(app, path(booking) + "/reschedule-slots?fromDate=2026-10-25&toDate=2026-10-26")
                    .get("items");
            assertEquals(List.of("2026-10-25T02:00:00+02:00", "2026-10-25T02:00:00+01:00"),
                    slots.valueStream().map(s -> s.get("startsAt").asString()).toList());
            var bridge = move(app, booking, "2026-10-25T02:15:00+02:00", "bridge");
            assertEquals(409, bridge.statusCode());
            assertEquals(booking, get(app, path(booking)));
            assertEquals(200, send(app, "PUT", "/api/owner/meeting-types/Type/date-overrides/2026-10-24",
                    "{\"intervals\":[{\"start\":\"23:45\",\"end\":\"24:00\"}],\"expectedVersion\":2}", "night")
                    .statusCode());
            assertEquals(200, send(app, "PUT", "/api/owner/meeting-types/Type/date-overrides/2026-10-25",
                    "{\"intervals\":[{\"start\":\"00:00\",\"end\":\"00:15\"}],\"expectedVersion\":3}", "morning")
                    .statusCode());
            var nightSlots = get(app, path(booking) + "/reschedule-slots?fromDate=2026-10-24&toDate=2026-10-25")
                    .get("items");
            assertEquals("2026-10-24T23:45:00+02:00", nightSlots.get(0).get("startsAt").asString());
            assertEquals("2026-10-25T00:15:00+02:00", nightSlots.get(0).get("endsAt").asString());
            var night = move(app, booking, "2026-10-24T23:45:00+02:00", "cross-midnight");
            assertEquals(200, night.statusCode(), night.body());
            assertEquals("2026-10-25T00:15:00+02:00", json.readTree(night.body()).get("endsAt").asString());
        }
    }

    @Test
    void springGapIsSkippedAndSavedElapsedDurationCrossesTheTransitionWithoutInventedWallTime() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        clock.set("2026-03-22T00:00:00Z");
        try (var app = start(clock)) {
            assertEquals(200, send(app, "PUT", "/api/owner/time-zone",
                    "{\"timeZone\":\"Europe/Berlin\",\"applyToActiveTypes\":false,\"expectedVersion\":1}", "zone")
                    .statusCode());
            var booking = book(app, "Type", true, "2026-03-23T09:00:00+01:00").get("booking");
            assertEquals(200, send(app, "PUT", "/api/owner/meeting-types/Type/date-overrides/2026-03-29",
                    "{\"intervals\":[{\"start\":\"01:30\",\"end\":\"03:30\"}],\"expectedVersion\":1}", "gap")
                    .statusCode());
            var slots = get(app, path(booking) + "/reschedule-slots?fromDate=2026-03-29&toDate=2026-03-30")
                    .get("items");
            assertEquals(List.of("2026-03-29T01:30:00+01:00", "2026-03-29T01:45:00+01:00",
                    "2026-03-29T03:00:00+02:00"), slots.valueStream().map(s -> s.get("startsAt").asString()).toList());
            var response = move(app, booking, "2026-03-29T01:45:00+01:00", "transition");
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("2026-03-29T03:15:00+02:00", json.readTree(response.body()).get("endsAt").asString());
        }
    }

    private HttpResponse<String> move(ConfigurableApplicationContext app, JsonNode booking, String start, String key)
            throws Exception {
        return send(app, "POST", path(booking) + "/reschedule", json.writeValueAsString(Map.of(
                "startsAt", start, "expectedVersion", booking.get("version").asInt())), key);
    }

    private JsonNode book(ConfigurableApplicationContext app, String id, boolean approval, String start)
            throws Exception {
        assertEquals(201, send(app, "POST", "/api/owner/meeting-types", json.writeValueAsString(Map.of(
                "id", id, "title", "Консультация " + id, "description", "Подробное описание консультации",
                "durationMinutes", 30, "requiresApproval", approval)), UUID.randomUUID().toString()).statusCode());
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
