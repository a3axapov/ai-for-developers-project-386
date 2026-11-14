package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

class WeeklyHoursHttpTests {
    private final JsonMapper json = JsonMapper.builder().build();
    private static final String PATH = "/api/owner/meeting-types/Consult/weekly-hours";

    @Test
    void canonicalMinuteIntervalsPreserveGapsAndMidnightGridAndOtherTypesTemplate() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            create(app, "Consult", false);
            var originalConditions = get(app, "/api/public/owner/meeting-types/Consult").get("meetingType");
            var body = hours(1, List.of(day("MONDAY", "09:10", "10:00", "09:30", "10:30",
                    "10:30", "11:00", "12:00", "13:00")));
            var response = send(app, "PUT", PATH, body, "hours");
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var type = json.readTree(response.body());
            assertEquals(2, type.get("version").asInt());
            assertEquals(7, type.get("days").size());
            assertEquals(json.readTree(json.writeValueAsString(day("MONDAY", "09:10", "11:00",
                    "12:00", "13:00"))), type.get("days").get(0));
            assertEquals(originalConditions.get("conditionsToken"),
                    get(app, "/api/public/owner/meeting-types/Consult").get("meetingType").get("conditionsToken"));
            var slots = get(app, "/api/public/owner/meeting-types/Consult/slots").get("items");
            assertEquals("2026-11-02T09:15:00+03:00", slots.get(0).get("startsAt").asString());
            for (var slot : slots) {
                assertFalse(slot.get("startsAt").asString().contains("T10:45"));
                assertFalse(slot.get("startsAt").asString().contains("T11:"));
            }
            create(app, "Other", false);
            assertEquals("09:00", get(app, "/api/owner/meeting-types/Other")
                    .get("days").get(0).get("intervals").get(0).get("start").asString());
            assertEquals(type, json.readTree(send(app, "PUT", PATH, body, "hours").body()));
            assertEquals(type, get(app, "/api/owner/operations/hours").get("meetingType"));
        }
    }

    private Map<String, Object> day(String name, String... bounds) {
        var intervals = new java.util.ArrayList<Map<String, String>>();
        for (int i = 0; i < bounds.length; i += 2) {
            intervals.add(Map.of("start", bounds[i], "end", bounds[i + 1]));
        }
        return Map.of("day", name, "intervals", intervals);
    }

    @Test
    void impactWarningIsAtomicAndBindsNewConsequencesAndCurrentBookingStatus() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            create(app, "Consult", true);
            var pending = book(app, "Consult", "2026-11-02T09:00:00+03:00");
            var body = hours(1, List.of(day("MONDAY", "10:00", "12:00")));
            var before = get(app, "/api/owner/meeting-types/Consult");
            var warning = send(app, "PUT", PATH, body, "preview");
            assertEquals(409, warning.statusCode(), warning.body());
            assertEquals("no-store", warning.headers().firstValue("Cache-Control").orElseThrow());
            var error = json.readTree(warning.body());
            assertEquals("IMPACT_CONFIRMATION_REQUIRED", error.get("code").asString());
            assertEquals(1, error.get("affectedBookings").size());
            assertEquals(pending.get("id"), error.get("affectedBookings").get(0).get("id"));
            assertEquals("PENDING", error.get("affectedBookings").get(0).get("status").asString());
            assertFalse(warning.body().contains("resultUrl"));
            assertFalse(warning.body().contains("accessToken"));
            assertEquals(before, get(app, "/api/owner/meeting-types/Consult"));
            var confirmed = send(app, "POST", "/api/owner/bookings/" + pending.get("id").asString()
                    + "/confirm", "{\"expectedVersion\":1}", "confirm");
            assertEquals(200, confirmed.statusCode(), confirmed.body());
            var stale = send(app, "PUT", PATH, consent(body, error), "consent-old");
            assertEquals(409, stale.statusCode(), stale.body());
            var fresh = json.readTree(stale.body());
            assertEquals("CONFIRMED", fresh.get("affectedBookings").get(0).get("status").asString());
            assertEquals(2, fresh.get("affectedBookings").get(0).get("version").asInt());
            assertNotEquals(error.get("confirmationToken"), fresh.get("confirmationToken"));
            assertEquals(before, get(app, "/api/owner/meeting-types/Consult"));
            var saved = send(app, "PUT", PATH, consent(body, fresh), "consent-new");
            assertEquals(200, saved.statusCode(), saved.body());
            assertEquals(json.readTree(confirmed.body()),
                    get(app, "/api/owner/bookings/" + pending.get("id").asString()));
            assertEquals(error, json.readTree(send(app, "PUT", PATH, body, "preview").body()));
            assertEquals(fresh, get(app, "/api/owner/operations/consent-old").get("error"));
            assertEquals(json.readTree(saved.body()), json.readTree(
                    send(app, "PUT", PATH, consent(body, fresh), "consent-new").body()));
            // Owner resolution: already-invalid bookings do not cause unrelated edits to warn again.
            assertEquals(200, send(app, "PUT", PATH, hours(2,
                    List.of(day("MONDAY", "10:00", "13:00"))), "unrelated").statusCode());
        }
    }

    private String consent(String body, JsonNode warning) {
        return body.substring(0, body.length() - 1) + ",\"confirmationToken\":\""
                + warning.get("confirmationToken").asString() + "\"}";
    }

    @Test
    void closedPendingStillOccupiesOtherTypesCannotConfirmAndHoursDoNotChangeGuestConditions() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            create(app, "Consult", true);
            create(app, "Other", false);
            var pending = book(app, "Consult", "2026-11-02T09:00:00+03:00");
            var conditions = get(app, "/api/public/owner/meeting-types/Consult").get("meetingType");
            var valid = prepare(app, "Consult", conditions, "2026-11-02T10:00:00+03:00");
            var closing = prepare(app, "Consult", conditions, "2026-11-02T11:00:00+03:00");
            var body = hours(1, List.of(day("MONDAY", "10:00", "10:30")));
            var error = json.readTree(send(app, "PUT", PATH, body, "preview").body());
            assertEquals(200, send(app, "PUT", PATH, consent(body, error), "agree").statusCode());
            var unchanged = get(app, "/api/owner/bookings/" + pending.get("id").asString());
            assertEquals(pending, unchanged);
            assertEquals(409, send(app, "POST", "/api/owner/bookings/" + pending.get("id").asString()
                    + "/confirm", "{\"expectedVersion\":1}", "forbidden").statusCode());
            assertEquals("SLOT_UNAVAILABLE", get(app, "/api/owner/operations/forbidden").get("error")
                    .get("code").asString());
            assertEquals(201, submit(app, valid).statusCode());
            var closed = submit(app, closing);
            assertEquals(409, closed.statusCode(), closed.body());
            assertEquals("SLOT_UNAVAILABLE", json.readTree(closed.body()).get("code").asString());
            assertEquals(json.readTree(closed.body()), json.readTree(submit(app, closing).body()));
            var otherConditions = get(app, "/api/public/owner/meeting-types/Other").get("meetingType");
            var conflict = submit(app, prepare(app, "Other", otherConditions, "2026-11-02T09:00:00+03:00"));
            assertEquals(409, conflict.statusCode());
            assertEquals("TIME_CONFLICT", json.readTree(conflict.body()).get("code").asString());
            assertEquals(pending, get(app, "/api/owner/bookings/" + pending.get("id").asString()));
        }
    }

    private JsonNode prepare(ConfigurableApplicationContext app, String type, JsonNode conditions, String time)
            throws Exception {
        var prepared = send(app, "POST", "/api/public/owner/booking-attempts",
                json.writeValueAsString(Map.of("meetingTypeId", type,
                        "conditionsToken", conditions.get("conditionsToken").asString(),
                        "startsAt", time, "guestName", "Гость", "guestEmail", "guest@example.com")), null);
        assertEquals(201, prepared.statusCode(), prepared.body());
        return json.readTree(prepared.body());
    }

    @Test
    void impactExcludesStartedTerminalAndExpiredRecordsAndRechecksExpiryBeforeConsent() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        try (var app = start(clock)) {
            create(app, "Consult", true);
            var expired = book(app, "Consult", "2026-11-02T09:00:00+03:00");
            var started = book(app, "Consult", "2026-11-02T09:30:00+03:00");
            var rejected = book(app, "Consult", "2026-11-02T10:00:00+03:00");
            var cancelled = book(app, "Consult", "2026-11-02T10:30:00+03:00");
            var futurePending = book(app, "Consult", "2026-11-02T11:00:00+03:00");
            var futureConfirmed = book(app, "Consult", "2026-11-02T11:30:00+03:00");
            decide(app, started, "confirm", 1);
            decide(app, rejected, "reject", 1);
            decide(app, cancelled, "confirm", 1);
            decide(app, cancelled, "cancel", 2);
            decide(app, futureConfirmed, "confirm", 1);
            clock.set("2026-11-02T07:00:00Z"); // 10:00 local: started/expired/terminal excluded.
            var body = hours(1, List.of());
            var warning = json.readTree(send(app, "PUT", PATH, body, "preview").body());
            assertEquals(2, warning.get("affectedBookings").size());
            assertEquals(futurePending.get("id"), warning.get("affectedBookings").get(0).get("id"));
            assertEquals(futureConfirmed.get("id"), warning.get("affectedBookings").get(1).get("id"));
            clock.set("2026-11-02T08:00:00Z"); // exact pending start must expire before impact comparison.
            var refreshed = send(app, "PUT", PATH, consent(body, warning), "expire-consent");
            assertEquals(409, refreshed.statusCode(), refreshed.body());
            var fresh = json.readTree(refreshed.body());
            assertEquals(1, fresh.get("affectedBookings").size());
            assertEquals(futureConfirmed.get("id"), fresh.get("affectedBookings").get(0).get("id"));
            assertEquals(1, get(app, "/api/owner/meeting-types/Consult").get("version").asInt());
            assertEquals("REJECTED", get(app, "/api/owner/bookings/" + expired.get("id").asString())
                    .get("status").asString());
            assertEquals("REJECTED", get(app, "/api/owner/bookings/" + futurePending.get("id").asString())
                    .get("status").asString());
            assertEquals(200, send(app, "PUT", PATH, consent(body, fresh), "final-consent").statusCode());
        }
    }

    private void decide(ConfigurableApplicationContext app, JsonNode booking, String action, int version)
            throws Exception {
        var response = send(app, "POST", "/api/owner/bookings/" + booking.get("id").asString() + "/" + action,
                "{\"expectedVersion\":" + version + "}", UUID.randomUUID().toString());
        assertEquals(200, response.statusCode(), response.body());
    }

    @Test
    void tokenCannotAuthorizeAnotherProposalOrTypeAndChangedImpactRequiresFreshConsent() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            create(app, "Consult", true);
            create(app, "Other", true);
            book(app, "Consult", "2026-11-02T09:00:00+03:00");
            book(app, "Other", "2026-11-02T09:30:00+03:00");
            var body = hours(1, List.of(day("MONDAY", "12:00", "13:00")));
            var warning = json.readTree(send(app, "PUT", PATH, body, "preview").body());
            var wrongProposal = send(app, "PUT", PATH, consent(hours(1, List.of()), warning), "other-proposal");
            assertEquals(409, wrongProposal.statusCode());
            assertEquals("IMPACT_CONFIRMATION_REQUIRED", json.readTree(wrongProposal.body()).get("code").asString());
            var wrongType = send(app, "PUT", PATH.replace("Consult", "Other"),
                    consent(body, warning), "other-type");
            assertEquals(409, wrongType.statusCode());
            assertEquals("IMPACT_CONFIRMATION_REQUIRED", json.readTree(wrongType.body()).get("code").asString());
            book(app, "Consult", "2026-11-02T10:00:00+03:00");
            assertEquals(409, send(app, "PUT", PATH, consent(body, warning), "preview").statusCode());
            assertEquals(warning, get(app, "/api/owner/operations/preview").get("error"));
            var changed = send(app, "PUT", PATH, consent(body, warning), "changed");
            assertEquals(409, changed.statusCode());
            var fresh = json.readTree(changed.body());
            assertEquals(2, fresh.get("affectedBookings").size());
            assertEquals(1, get(app, "/api/owner/meeting-types/Consult").get("version").asInt());
            assertEquals(200, send(app, "PUT", PATH, consent(body, fresh), "agree").statusCode());
            assertEquals(warning, json.readTree(send(app, "PUT", PATH, body, "preview").body()));
            assertEquals(1, get(app, "/api/owner/meeting-types/Other").get("version").asInt());
        }
    }

    @Test
    void concurrentSubmissionAndHoursSaveNeverCreateUnacknowledgedClosedBooking() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            create(app, "Consult", false);
            var conditions = get(app, "/api/public/owner/meeting-types/Consult").get("meetingType");
            var prepared = prepare(app, "Consult", conditions, "2026-11-02T09:00:00+03:00");
            var gate = new java.util.concurrent.CyclicBarrier(3);
            try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var submission = executor.submit(() -> {
                    gate.await();
                    return submit(app, prepared);
                });
                var edit = executor.submit(() -> {
                    gate.await();
                    return send(app, "PUT", PATH, hours(1, List.of()), "racing-edit");
                });
                gate.await();
                var booked = submission.get();
                var edited = edit.get();
                if (booked.statusCode() == 201) {
                    assertEquals(409, edited.statusCode(), edited.body());
                    assertEquals(1, json.readTree(edited.body()).get("affectedBookings").size());
                    assertEquals(1, get(app, "/api/owner/meeting-types/Consult").get("version").asInt());
                } else {
                    assertEquals(409, booked.statusCode(), booked.body());
                    assertEquals("SLOT_UNAVAILABLE", json.readTree(booked.body()).get("code").asString());
                    assertEquals(200, edited.statusCode(), edited.body());
                    assertEquals(0, get(app, "/api/owner/bookings?view=upcoming").get("items").size());
                }
            }
        }
    }

    @Test
    void concurrentNewBookingAndConsentRecomputeMaterialImpactAtomically() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            create(app, "Consult", true);
            book(app, "Consult", "2026-11-02T09:00:00+03:00");
            var conditions = get(app, "/api/public/owner/meeting-types/Consult").get("meetingType");
            var prepared = prepare(app, "Consult", conditions, "2026-11-02T10:00:00+03:00");
            var body = hours(1, List.of());
            var warning = json.readTree(send(app, "PUT", PATH, body, "preview").body());
            var gate = new java.util.concurrent.CyclicBarrier(3);
            try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var submission = executor.submit(() -> {
                    gate.await();
                    return submit(app, prepared);
                });
                var edit = executor.submit(() -> {
                    gate.await();
                    return send(app, "PUT", PATH, consent(body, warning), "racing-consent");
                });
                gate.await();
                var booked = submission.get();
                var edited = edit.get();
                if (booked.statusCode() == 201) {
                    assertEquals(409, edited.statusCode(), edited.body());
                    assertEquals(2, json.readTree(edited.body()).get("affectedBookings").size());
                    assertEquals(1, get(app, "/api/owner/meeting-types/Consult").get("version").asInt());
                } else {
                    assertEquals(409, booked.statusCode(), booked.body());
                    assertEquals("SLOT_UNAVAILABLE", json.readTree(booked.body()).get("code").asString());
                    assertEquals(200, edited.statusCode(), edited.body());
                    assertEquals(1, get(app, "/api/owner/bookings?view=upcoming").get("items").size());
                }
                assertEquals(warning, get(app, "/api/owner/operations/preview").get("error"));
            }
        }
    }

    @Test
    void midnightUsesAdjacentDaysAndDoesNotInventCoverageAcrossClosedDay() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            create(app, "Consult", false);
            var midnight = hours(1, List.of(day("MONDAY", "23:00", "24:00"), day("TUESDAY", "00:00", "01:00")));
            assertEquals(200, send(app, "PUT", PATH, midnight, "midnight").statusCode());
            var slots = get(app, "/api/public/owner/meeting-types/Consult/slots").get("items");
            assertTrue(slots.valueStream().anyMatch(slot -> slot.get("startsAt").asString()
                    .equals("2026-11-02T23:45:00+03:00")
                    && slot.get("endsAt").asString().equals("2026-11-03T00:15:00+03:00")));
            assertEquals(200, send(app, "PUT", PATH, hours(2,
                    List.of(day("MONDAY", "23:00", "24:00"))), "close-next-day").statusCode());
            var closed = get(app, "/api/public/owner/meeting-types/Consult/slots").get("items");
            assertFalse(closed.valueStream().anyMatch(slot -> slot.get("startsAt").asString().contains("T23:45")));
        }
    }

    @Test
    void editedRepeatedDstIntervalKeepsBothOccurrencesWithoutOpeningRealGap() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        clock.set("2026-10-24T09:00:00Z");
        try (var app = new SpringApplicationBuilder(CalendarApplication.class)
                .initializers(context -> context.getBeanFactory().registerSingleton("testClock", clock))
                .run("--server.port=0", "--calendar.initial.booking-enabled=true",
                        "--calendar.initial.time-zone=Europe/Berlin")) {
            create(app, "Consult", false);
            assertEquals(200, send(app, "PUT", PATH, hours(1, List.of(day("SUNDAY", "02:15", "02:45"))),
                    "dst-hours").statusCode());
            var slots = get(app, "/api/public/owner/meeting-types/Consult/slots").get("items");
            var transition = slots.valueStream().filter(slot -> slot.get("startsAt").asString()
                    .startsWith("2026-10-25")).toList();
            assertEquals(2, transition.size());
            assertEquals("2026-10-25T02:15:00+02:00", transition.get(0).get("startsAt").asString());
            assertEquals("2026-10-25T02:45:00+02:00", transition.get(0).get("endsAt").asString());
            assertEquals("2026-10-25T02:15:00+01:00", transition.get(1).get("startsAt").asString());
            assertEquals("2026-10-25T02:45:00+01:00", transition.get(1).get("endsAt").asString());
        }
    }

    @Test
    void invalidCommandsRetainFailuresWithoutPartialChangesAndArchivedTypesRejectEdits() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            create(app, "Consult", false);
            var original = get(app, "/api/owner/meeting-types/Consult");
            for (var body : List.of(
                    "{\"expectedVersion\":1,\"days\":[{\"day\":\"FUNDAY\",\"intervals\":[]}]}",
                    "{\"expectedVersion\":1,\"days\":null}", "{\"expectedVersion\":0,\"days\":[]}",
                    hours(1, List.of(day("MONDAY", "24:00", "24:00"))),
                    hours(1, List.of(day("MONDAY", "09:00", "09:00"))),
                    hours(1, List.of(day("MONDAY", "23:00", "01:00"))),
                    hours(1, List.of(day("MONDAY", "9:00", "10:00"))),
                    hours(1, List.of(day("MONDAY", "09:60", "10:00"))),
                    hours(1, List.of(day("MONDAY", "09:00:00", "10:00"))),
                    hours(1, List.of(day("MONDAY"), day("MONDAY"))),
                    "{")) {
                var key = UUID.randomUUID().toString();
                var failed = send(app, "PUT", PATH, body, key);
                assertTrue(List.of(400, 422).contains(failed.statusCode()), failed.body());
                var error = json.readTree(failed.body());
                assertEquals(error, get(app, "/api/owner/operations/" + key).get("error"));
                assertEquals(error, json.readTree(send(app, "PUT", PATH, body, key).body()));
                assertEquals(failed.statusCode() == 400 ? 409 : 422,
                        send(app, "PUT", PATH, body + " ", key).statusCode());
                assertEquals(original, get(app, "/api/owner/meeting-types/Consult"));
            }
            for (var key : java.util.Arrays.asList(null, "bad.key", "x".repeat(129))) {
                assertEquals(400, send(app, "PUT", PATH, hours(1, List.of()), key).statusCode());
            }
            assertEquals(404, send(app, "PUT", PATH.replace("Consult", "Missing"),
                    hours(1, List.of()), "missing").statusCode());
            assertEquals(409, send(app, "PUT", PATH, hours(2, List.of()), "stale").statusCode());
            assertEquals(200, send(app, "POST", "/api/owner/meeting-types/Consult/archive",
                    "{\"expectedVersion\":1}", "archive").statusCode());
            var archived = get(app, "/api/owner/meeting-types/Consult");
            var rejected = send(app, "PUT", PATH, hours(2, List.of()), "archived-hours");
            assertEquals(409, rejected.statusCode());
            assertEquals("ACTION_NOT_ALLOWED", json.readTree(rejected.body()).get("code").asString());
            assertEquals(archived, get(app, "/api/owner/meeting-types/Consult"));
        }
    }

    private JsonNode book(ConfigurableApplicationContext app, String type, String time) throws Exception {
        var conditions = get(app, "/api/public/owner/meeting-types/" + type).get("meetingType");
        var prepared = send(app, "POST", "/api/public/owner/booking-attempts",
                json.writeValueAsString(Map.of("meetingTypeId", type,
                        "conditionsToken", conditions.get("conditionsToken").asString(),
                        "startsAt", time, "guestName", "Гость", "guestEmail", "guest@example.com")), null);
        assertEquals(201, prepared.statusCode(), prepared.body());
        var response = submit(app, json.readTree(prepared.body()));
        assertEquals(201, response.statusCode(), response.body());
        return json.readTree(response.body()).get("booking");
    }

    private HttpResponse<String> submit(ConfigurableApplicationContext app, JsonNode prepared) throws Exception {
        int port = ((WebServerApplicationContext) app).getWebServer().getPort();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                    + "/api/booking-attempts/" + prepared.get("attemptId").asString() + "/submit"))
                    .header("Authorization", "Bearer " + prepared.get("attemptSecret").asString())
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private String hours(int version, List<Map<String, Object>> days) {
        return json.writeValueAsString(Map.of("expectedVersion", version, "days", days));
    }

    private void create(ConfigurableApplicationContext app, String id, boolean approval) throws Exception {
        var result = send(app, "POST", "/api/owner/meeting-types", json.writeValueAsString(Map.of(
                "id", id, "title", id, "description", "Достаточно длинное описание встречи",
                "durationMinutes", 30, "requiresApproval", approval)), UUID.randomUUID().toString());
        assertEquals(201, result.statusCode(), result.body());
    }

    private ConfigurableApplicationContext start(BookingSubmissionHttpTests.MutableClock clock) {
        return new SpringApplicationBuilder(CalendarApplication.class)
                .initializers(context -> context.getBeanFactory().registerSingleton("testClock", clock))
                .run("--server.port=0", "--calendar.initial.booking-enabled=true");
    }

    private JsonNode get(ConfigurableApplicationContext app, String path) throws Exception {
        var result = send(app, "GET", path, null, null);
        assertEquals(200, result.statusCode(), result.body());
        assertEquals("no-store", result.headers().firstValue("Cache-Control").orElseThrow());
        return json.readTree(result.body());
    }

    private HttpResponse<String> send(ConfigurableApplicationContext app, String method, String path,
            String body, String key) throws Exception {
        int port = ((WebServerApplicationContext) app).getWebServer().getPort();
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .header("Content-Type", "application/json");
            if (key != null) {
                request.header("Idempotency-Key", key);
            }
            return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
