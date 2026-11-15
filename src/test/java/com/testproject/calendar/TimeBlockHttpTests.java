package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TimeBlockHttpTests {
    private final JsonMapper json = JsonMapper.builder().build();
    private static final String BLOCKS = "/api/owner/time-blocks";
    private static final String CALENDAR = "/api/owner/calendar?from=2026-01-01T00:00:00Z&to=2027-01-01T00:00:00Z";

    @Test
    void pastOvernightBlockIsSeparateCalendarOccupancyWithVersionedCrudAndReadOnlyJournal() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            var body = draft("Личное", "2026-01-01T23:00:00+03:00", "2026-01-03T06:00:00+03:00");
            var response = send(app, "POST", BLOCKS, body, "create");
            assertEquals(201, response.statusCode(), response.body());
            var result = json.readTree(response.body());
            var block = result.get("timeBlock");
            assertEquals(0, result.get("rejectedBookingIds").size());
            assertEquals("Личное", block.get("title").asString());
            assertEquals(1, block.get("version").asInt());
            var calendar = get(app, CALENDAR);
            assertEquals(0, calendar.get("items").size());
            assertEquals(block, calendar.get("timeBlocks").get(0));
            assertEquals(result, get(app, "/api/owner/operations/create").get("timeBlockResult"));
            assertEquals(result, json.readTree(send(app, "POST", BLOCKS, body, "create").body()));
            var path = BLOCKS + "/" + block.get("id").asString();
            var edit = draft("Новое", "2026-01-01T23:00:00+03:00", "2026-01-03T06:00:00+03:00");
            var edited = send(app, "PUT", path, version(edit, 1), "edit");
            assertEquals(200, edited.statusCode(), edited.body());
            assertEquals(2, json.readTree(edited.body()).get("timeBlock").get("version").asInt());
            assertEquals(409, send(app, "DELETE", path, "{\"expectedVersion\":1}", "stale").statusCode());
            assertEquals(204, send(app, "DELETE", path, "{\"expectedVersion\":2}", "delete").statusCode());
            assertEquals(204, get(app, "/api/owner/operations/delete").get("statusCode").asInt());
            assertEquals(204, send(app, "DELETE", path, "{\"expectedVersion\":2}", "delete").statusCode());
            assertEquals(0, get(app, CALENDAR).get("timeBlocks").size());
            assertEquals(410, send(app, "GET", "/api/owner/operations/missing", null, null).statusCode());
        }
    }

    @Test
    void literalOwnerLocalTimeRejectsGapsWrongOffsetsAndInvalidRangesWithoutSilentShift() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Berlin")) {
            var invalid = java.util.List.of(
                    draft(" ", "2026-10-25T02:00:00+02:00", "2026-10-25T03:00:00+01:00"),
                    draft("Gap", "2026-03-29T02:30:00+01:00", "2026-03-29T04:00:00+02:00"),
                    draft("Wrong fold", "2026-10-25T02:30:00+03:00", "2026-10-25T04:00:00+01:00"),
                    draft("Wrong end", "2026-10-25T01:00:00+02:00", "2026-10-25T04:00:00+02:00"),
                    draft("Equal", "2026-10-25T02:30:00+01:00", "2026-10-25T02:30:00+01:00"),
                    draft("Reversed", "2026-10-25T02:30:00+01:00", "2026-10-25T02:30:00+02:00"));
            for (int i = 0; i < invalid.size(); i++) {
                var response = send(app, "POST", BLOCKS, invalid.get(i), "invalid-" + i);
                assertEquals(422, response.statusCode(), response.body());
                assertEquals("VALIDATION_FAILED", json.readTree(response.body()).get("code").asString());
                assertFalse(json.readTree(response.body()).get("fieldErrors").isEmpty());
                assertEquals(0, get(app, CALENDAR).get("timeBlocks").size());
            }
            // Same local hour, different explicit choices: positive elapsed duration is valid.
            var fold = send(app, "POST", BLOCKS,
                    draft("Fold", "2026-10-25T02:30:00+02:00", "2026-10-25T02:30:00+01:00"), "fold");
            assertEquals(201, fold.statusCode(), fold.body());
            assertEquals("2026-10-25T02:30:00+02:00",
                    json.readTree(fold.body()).get("timeBlock").get("startsAt").asString());
            var noOffset = draft("Missing offset", "2026-10-25T02:30:00", "2026-10-25T04:00:00+01:00");
            assertEquals(400, send(app, "POST", BLOCKS, noOffset, "no-offset").statusCode());
            assertEquals(400, get(app, "/api/owner/operations/no-offset").get("statusCode").asInt());
            assertEquals(409, send(app, "DELETE", BLOCKS + "/unknown", noOffset, "no-offset").statusCode());
        }
    }

    @Test
    void halfOpenGlobalOccupancyIncludesPastConfirmedAndBlocksButExcludesEditedSelf() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        try (var app = start(clock, "Europe/Moscow")) {
            createType(app, "Auto", false);
            createType(app, "Other", false);
            var confirmed = book(app, "Auto", "2026-11-02T09:00:00+03:00").get("booking");
            var prepared = prepare(app, "Other", "2026-11-02T10:00:00+03:00");
            var body = draft("Private appointment", "2026-11-02T10:00:00+03:00", "2026-11-02T10:30:00+03:00");
            var block = json.readTree(send(app, "POST", BLOCKS, body, "create").body()).get("timeBlock");
            assertEquals(409, send(app, "POST", BLOCKS, body, "overlap").statusCode());
            assertEquals("TIME_CONFLICT", json.readTree(submit(app, prepared).body()).get("code").asString());
            for (var type : java.util.List.of("Auto", "Other")) {
                var slots = get(app, "/api/public/owner/meeting-types/" + type + "/slots");
                assertFalse(slots.toString().contains("Private appointment"));
                assertFalse(slots.get("items").valueStream().anyMatch(slot ->
                        slot.get("startsAt").asString().startsWith("2026-11-02T10:00")
                                || slot.get("startsAt").asString().startsWith("2026-11-02T09:45")));
                assertTrue(slots.get("items").valueStream().anyMatch(slot ->
                        slot.get("startsAt").asString().startsWith("2026-11-02T09:30")));
            }
            assertEquals(200, send(app, "PUT", BLOCKS + "/" + block.get("id").asString(), version(body, 1),
                    "self").statusCode());
            assertEquals(201, send(app, "POST", BLOCKS,
                    draft("Touch", "2026-11-02T10:30:00+03:00", "2026-11-02T11:00:00+03:00"), "touch").statusCode());
            clock.set("2026-11-04T09:00:00Z");
            var conflict = send(app, "POST", BLOCKS,
                    draft("Past", "2026-11-02T09:15:00+03:00", "2026-11-02T09:30:00+03:00"), "past");
            assertEquals(409, conflict.statusCode(), conflict.body());
            assertEquals("TIME_CONFLICT", json.readTree(conflict.body()).get("code").asString());
            var past = get(app, "/api/owner/bookings/" + confirmed.get("id").asString());
            assertEquals(confirmed.get("status"), past.get("status"));
            assertEquals(confirmed.get("version"), past.get("version"));
            assertEquals(confirmed.get("startsAt"), past.get("startsAt"));
            assertEquals(2, get(app, CALENDAR).get("timeBlocks").size());
        }
    }

    @Test
    void freshConsentAtomicallyRejectsAllPendingAndBindsDraftAndChangedImpactIncludingDisappearance() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            createType(app, "Manual", true);
            var first = book(app, "Manual", "2026-11-02T09:00:00+03:00");
            var hours = "{\"expectedVersion\":1,\"intervals\":[{\"start\":\"09:30\",\"end\":\"18:00\"}]}";
            var hoursPath = "/api/owner/meeting-types/Manual/date-overrides/2026-11-02";
            var hoursWarning = json.readTree(send(app, "PUT", hoursPath, hours, "hours-preview").body());
            assertEquals(200, send(app, "PUT", hoursPath, consent(hours, hoursWarning), "hours-agree").statusCode());
            // Availability consent kept this pending request; block consent MUST still reject it.
            var body = draft("Private", "2026-11-02T09:00:00+03:00", "2026-11-02T11:00:00+03:00");
            var response = send(app, "POST", BLOCKS, body, "preview");
            assertEquals(409, response.statusCode(), response.body());
            var warning = json.readTree(response.body());
            assertEquals("IMPACT_CONFIRMATION_REQUIRED", warning.get("code").asString());
            assertEquals(1, warning.get("affectedBookings").size());
            assertEquals(first.get("booking").get("id"), warning.get("affectedBookings").get(0).get("id"));
            assertEquals(first.get("booking"), get(app, "/api/owner/bookings/"
                    + first.get("booking").get("id").asString()));
            assertEquals(0, get(app, CALENDAR).get("timeBlocks").size());
            var second = book(app, "Manual", "2026-11-02T10:00:00+03:00");
            var stale = send(app, "POST", BLOCKS, consent(body, warning), "stale");
            assertEquals(409, stale.statusCode(), stale.body());
            var fresh = json.readTree(stale.body());
            assertEquals(2, fresh.get("affectedBookings").size());
            var changedTitle = body.replace("Private", "Changed");
            var changed = send(app, "POST", BLOCKS, consent(changedTitle, fresh), "changed-title");
            assertEquals(409, changed.statusCode(), changed.body());
            var latest = json.readTree(changed.body());
            assertEquals(0, get(app, CALENDAR).get("timeBlocks").size());
            var saved = send(app, "POST", BLOCKS, consent(changedTitle, latest), "agree");
            assertEquals(201, saved.statusCode(), saved.body());
            var result = json.readTree(saved.body());
            assertEquals(2, result.get("rejectedBookingIds").size());
            for (var booking : java.util.List.of(first, second)) {
                var rejected = get(app, "/api/owner/bookings/" + booking.get("booking").get("id").asString());
                assertEquals("REJECTED", rejected.get("status").asString());
                assertEquals(2, rejected.get("version").asInt());
                assertEquals(rejected, get(app,
                        booking.get("resultUrl").asString().replace("/bookings/", "/api/bookings/")));
                assertEquals(409, send(app, "POST", "/api/owner/bookings/" + rejected.get("id").asString()
                        + "/confirm", "{\"expectedVersion\":2}",
                        "confirm-" + rejected.get("id").asString()).statusCode());
            }
            assertEquals(result,
                    json.readTree(send(app, "POST", BLOCKS, consent(changedTitle, latest), "agree").body()));
            assertEquals(warning, get(app, "/api/owner/operations/preview").get("error"));
            assertEquals(409, send(app, "POST", BLOCKS, body, "preview").statusCode());
            assertEquals(204, send(app, "DELETE", BLOCKS + "/" + result.get("timeBlock").get("id").asString(),
                    "{\"expectedVersion\":1}", "delete").statusCode());
            assertEquals("REJECTED", get(app, "/api/owner/bookings/" + first.get("booking").get("id").asString())
                    .get("status").asString());

            var third = book(app, "Manual", "2026-11-02T12:00:00+03:00").get("booking");
            var laterBody = draft("Later", "2026-11-02T12:00:00+03:00", "2026-11-02T13:00:00+03:00");
            var laterWarning = json.readTree(send(app, "POST", BLOCKS, laterBody, "later-preview").body());
            assertEquals(200, send(app, "POST", "/api/owner/bookings/" + third.get("id").asString() + "/reject",
                    "{\"expectedVersion\":1}", "reject").statusCode());
            var disappeared = send(app, "POST", BLOCKS, consent(laterBody, laterWarning), "disappeared");
            assertEquals(409, disappeared.statusCode(), disappeared.body());
            var emptyWarning = json.readTree(disappeared.body());
            assertEquals(0, emptyWarning.get("affectedBookings").size());
            assertEquals(201, send(app, "POST", BLOCKS, consent(laterBody, emptyWarning), "empty-agree").statusCode());
        }
    }

    @Test
    void confirmedConflictPreemptsAnyPendingRejectionIncludingStaleConsentAndPastConfirmed() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        try (var app = start(clock, "Europe/Moscow")) {
            createType(app, "Manual", true);
            var pending = book(app, "Manual", "2026-11-02T10:00:00+03:00").get("booking");
            var confirmed = book(app, "Manual", "2026-11-02T09:00:00+03:00").get("booking");
            var body = draft("Both", "2026-11-02T09:00:00+03:00", "2026-11-02T11:00:00+03:00");
            var warning = json.readTree(send(app, "POST", BLOCKS, body, "preview").body());
            assertEquals(200, send(app, "POST", "/api/owner/bookings/" + confirmed.get("id").asString() + "/confirm",
                    "{\"expectedVersion\":1}", "confirm").statusCode());
            var response = send(app, "POST", BLOCKS, consent(body, warning), "agree");
            assertEquals(409, response.statusCode());
            assertEquals("TIME_CONFLICT", json.readTree(response.body()).get("code").asString());
            assertFalse(json.readTree(response.body()).has("confirmationToken"));
            assertEquals(pending, get(app, "/api/owner/bookings/" + pending.get("id").asString()));
            assertEquals(0, get(app, CALENDAR).get("timeBlocks").size());
            clock.set("2026-11-02T06:45:00Z"); // confirmed has finished, pending has not begun.
            var pastConflict = send(app, "POST", BLOCKS, body, "past-confirmed");
            assertEquals("TIME_CONFLICT", json.readTree(pastConflict.body()).get("code").asString());
            assertEquals(pending, get(app, "/api/owner/bookings/" + pending.get("id").asString()));
        }
    }

    @Test
    void pendingExpiresExactlyAtStartBeforeImpactAndStaleConsentRequiresFreshEmptyWarning() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        try (var app = start(clock, "Europe/Moscow")) {
            createType(app, "Manual", true);
            var pending = book(app, "Manual", "2026-11-02T09:00:00+03:00").get("booking");
            var body = draft("Expired", "2026-11-02T09:00:00+03:00", "2026-11-02T09:30:00+03:00");
            clock.set("2026-11-02T05:59:59.999999999Z");
            var warning = json.readTree(send(app, "POST", BLOCKS, body, "preview").body());
            assertEquals(1, warning.get("affectedBookings").size());
            clock.set("2026-11-02T06:00:00Z");
            var response = send(app, "POST", BLOCKS, consent(body, warning), "stale");
            assertEquals(409, response.statusCode());
            var empty = json.readTree(response.body());
            assertEquals(0, empty.get("affectedBookings").size());
            assertEquals(0, get(app, CALENDAR).get("timeBlocks").size());
            var expired = get(app, "/api/owner/bookings/" + pending.get("id").asString());
            assertEquals("APPROVAL_EXPIRED", expired.get("rejectionReason").asString());
            assertEquals(2, expired.get("version").asInt());
            var saved = send(app, "POST", BLOCKS, consent(body, empty), "fresh");
            assertEquals(201, saved.statusCode(), saved.body());
            assertEquals(0, json.readTree(saved.body()).get("rejectedBookingIds").size());
            assertEquals(expired, get(app, "/api/owner/bookings/" + pending.get("id").asString()));
        }
    }

    @Test
    void actualConcurrentAutomaticSubmissionAndConsentedBlockOrConfirmationHaveOneAtomicWinner() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            createType(app, "Auto", false);
            createType(app, "Manual", true);
            for (int i = 0; i < 6; i++) {
                String start = "2026-11-02T%02d:00:00+03:00".formatted(9 + i);
                String end = "2026-11-02T%02d:30:00+03:00".formatted(9 + i);
                var body = draft("Race", start, end);
                if (i < 3) {
                    var attempt = prepare(app, "Auto", start);
                    String key = "auto-race-" + i;
                    var results = race(() -> submit(app, attempt), () -> send(app, "POST", BLOCKS, body, key));
                    assertEquals(1, results.stream().filter(result -> result.statusCode() == 201).count());
                    assertEquals(1, results.stream().filter(result -> result.statusCode() == 409).count());
                    assertEquals("TIME_CONFLICT", json.readTree(results.stream()
                            .filter(result -> result.statusCode() == 409).findFirst().orElseThrow().body())
                            .get("code").asString());
                } else {
                    var booking = book(app, "Manual", start).get("booking");
                    String key = "manual-race-" + i;
                    var warning = json.readTree(send(app, "POST", BLOCKS, body, key + "-preview").body());
                    var results = race(() -> send(app, "POST", "/api/owner/bookings/" + booking.get("id").asString()
                                    + "/confirm", "{\"expectedVersion\":1}", key + "-confirm"),
                            () -> send(app, "POST", BLOCKS, consent(body, warning), key));
                    assertEquals(1, results.stream().filter(result -> result.statusCode() < 300).count());
                    var current = get(app, "/api/owner/bookings/" + booking.get("id").asString());
                    assertEquals(2, current.get("version").asInt());
                    assertEquals(results.get(0).statusCode() == 200 ? "CONFIRMED" : "REJECTED",
                            current.get("status").asString());
                }
                var calendar = get(app, CALENDAR);
                var blocks = calendar.get("timeBlocks").valueStream().filter(block ->
                        block.get("startsAt").asString().equals(start)).count();
                var confirmed = calendar.get("items").valueStream().filter(booking ->
                        booking.get("startsAt").asString().equals(start)
                                && booking.get("status").asString().equals("CONFIRMED")).count();
                assertEquals(1, blocks + confirmed);
                assertFalse(get(app, "/api/public/owner/meeting-types/Auto/slots").get("items").valueStream()
                        .anyMatch(slot -> slot.get("startsAt").asString().equals(start)));
            }
        }
    }

    @Test
    void blockPersistsAcrossAvailabilityAndVacationAndDeletionNeverOpensOtherRestrictions() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        try (var app = start(clock, "Europe/Moscow")) {
            createType(app, "Auto", false);
            var body = draft("Night", "2026-11-02T01:00:00+03:00", "2026-11-03T11:00:00+03:00");
            var response = send(app, "POST", BLOCKS, body, "night");
            assertEquals(201, response.statusCode(), response.body());
            var block = json.readTree(response.body()).get("timeBlock");
            var vacation = send(app, "POST", "/api/owner/out-of-office",
                    "{\"startDate\":\"2026-11-02\",\"endDate\":\"2026-11-02\"}", "vacation");
            assertEquals(201, vacation.statusCode(), vacation.body());
            assertEquals(200, send(app, "PUT", "/api/owner/meeting-types/Auto/weekly-hours",
                    "{\"expectedVersion\":1,\"days\":[]}", "hours").statusCode());
            assertEquals(block, get(app, CALENDAR).get("timeBlocks").get(0));
            var far = send(app, "POST", BLOCKS,
                    draft("Far", "2028-11-02T01:00:00+03:00", "2028-11-03T11:00:00+03:00"), "far");
            assertEquals(201, far.statusCode(), far.body());
            clock.set("2026-11-02T06:00:00Z"); // Already started, remains editable/deletable.
            var updated = send(app, "PUT", BLOCKS + "/" + block.get("id").asString(),
                    version(body.replace("Night", "Started"), 1), "started");
            assertEquals(200, updated.statusCode(), updated.body());
            assertEquals(204, send(app, "DELETE", BLOCKS + "/" + block.get("id").asString(),
                    "{\"expectedVersion\":2}", "delete").statusCode());
            assertEquals(0, get(app, "/api/public/owner/meeting-types/Auto/slots").get("items").size());
            assertEquals(2, get(app, "/api/owner/meeting-types/Auto").get("version").asInt());
        }
    }

    @Test
    void editConsentBindsOperationIdVersionAndOffsetAndRetainedFailuresNeverReexecute() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            createType(app, "Manual", true);
            var pending = book(app, "Manual", "2026-11-02T10:00:00+03:00").get("booking");
            var original = draft("Before", "2026-11-02T07:00:00+03:00", "2026-11-02T08:00:00+03:00");
            var created = json.readTree(send(app, "POST", BLOCKS, original, "create").body()).get("timeBlock");
            String path = BLOCKS + "/" + created.get("id").asString();
            var proposal = draft("Edit", "2026-11-02T09:00:00+03:00", "2026-11-02T11:00:00+03:00");
            var warning = json.readTree(send(app, "PUT", path, version(proposal, 1), "preview").body());
            assertEquals("IMPACT_CONFIRMATION_REQUIRED", warning.get("code").asString());
            var wrongOperation = send(app, "POST", BLOCKS, consent(proposal, warning), "wrong-operation");
            assertEquals(409, wrongOperation.statusCode(), wrongOperation.body());
            assertEquals("IMPACT_CONFIRMATION_REQUIRED", json.readTree(wrongOperation.body()).get("code").asString());
            assertEquals(created, get(app, CALENDAR).get("timeBlocks").get(0));
            assertEquals(pending, get(app, "/api/owner/bookings/" + pending.get("id").asString()));
            var wrongOffset = proposal.replace("+03:00", "+02:00");
            assertEquals(422,
                    send(app, "PUT", path, consent(version(wrongOffset, 1), warning), "wrong-offset").statusCode());
            assertEquals(200, send(app, "PUT", path, version(original, 1), "other-tab").statusCode());
            var stale = send(app, "PUT", path, consent(version(proposal, 1), warning), "stale-version");
            assertEquals(409, stale.statusCode());
            assertEquals("RESOURCE_CHANGED", json.readTree(stale.body()).get("code").asString());
            var changed = send(app, "PUT", path, consent(version(proposal, 2), warning), "changed-version");
            assertEquals(409, changed.statusCode());
            var fresh = json.readTree(changed.body());
            assertEquals("IMPACT_CONFIRMATION_REQUIRED", fresh.get("code").asString());
            var saved = send(app, "PUT", path, consent(version(proposal, 2), fresh), "agree");
            assertEquals(200, saved.statusCode(), saved.body());
            assertEquals(3, json.readTree(saved.body()).get("timeBlock").get("version").asInt());
            assertEquals(1, json.readTree(saved.body()).get("rejectedBookingIds").size());
            assertEquals(warning, get(app, "/api/owner/operations/preview").get("error"));
            assertEquals(409, send(app, "PUT", path, version(proposal, 1), "preview").statusCode());
            assertEquals(409, send(app, "DELETE", path, "{\"expectedVersion\":3}", "agree").statusCode());
            assertEquals(400, send(app, "POST", BLOCKS, "{\"title\":", "malformed").statusCode());
            assertEquals(409, send(app, "PUT", path, "{\"title\":", "malformed").statusCode());
            assertEquals(400, get(app, "/api/owner/operations/malformed").get("statusCode").asInt());
            assertEquals(405, send(app, "PATCH", path, "{\"expectedVersion\":3}", "unused").statusCode());
            assertEquals(204, send(app, "DELETE", path, "{\"expectedVersion\":3}", "unused").statusCode());
            assertEquals(422, send(app, "DELETE", path, "{\"expectedVersion\":0}", "invalid-version").statusCode());
            assertEquals(404, send(app, "DELETE", path, "{\"expectedVersion\":3}", "missing").statusCode());
            assertEquals(0, get(app, CALENDAR).get("timeBlocks").size());
        }
    }

    private java.util.List<HttpResponse<String>> race(java.util.concurrent.Callable<HttpResponse<String>> first,
            java.util.concurrent.Callable<HttpResponse<String>> second) throws Exception {
        var gate = new java.util.concurrent.CyclicBarrier(3);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> {
                gate.await();
                return first.call();
            });
            var b = executor.submit(() -> {
                gate.await();
                return second.call();
            });
            gate.await();
            return java.util.List.of(a.get(), b.get());
        }
    }

    private String consent(String body, JsonNode warning) {
        return body.substring(0, body.length() - 1) + ",\"confirmationToken\":\""
                + warning.get("confirmationToken").asString() + "\"}";
    }

    private void createType(ConfigurableApplicationContext app, String id, boolean approval) throws Exception {
        var response = send(app, "POST", "/api/owner/meeting-types", json.writeValueAsString(Map.of(
                "id", id, "title", id, "description", "Достаточно длинное описание встречи",
                "durationMinutes", 30, "requiresApproval", approval)), "type-" + id);
        assertEquals(201, response.statusCode(), response.body());
    }

    private JsonNode prepare(ConfigurableApplicationContext app, String type, String time) throws Exception {
        var conditions = get(app, "/api/public/owner/meeting-types/" + type).get("meetingType");
        var response = send(app, "POST", "/api/public/owner/booking-attempts", json.writeValueAsString(Map.of(
                "meetingTypeId", type, "conditionsToken", conditions.get("conditionsToken").asString(),
                "startsAt", time, "guestName", "Гость", "guestEmail", "guest@example.com")), null);
        assertEquals(201, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private HttpResponse<String> submit(ConfigurableApplicationContext app, JsonNode attempt) throws Exception {
        int port = ((WebServerApplicationContext) app).getWebServer().getPort();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                    + "/api/booking-attempts/" + attempt.get("attemptId").asString() + "/submit"))
                    .header("Authorization", "Bearer " + attempt.get("attemptSecret").asString())
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private JsonNode book(ConfigurableApplicationContext app, String type, String time) throws Exception {
        var response = submit(app, prepare(app, type, time));
        assertEquals(201, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private String draft(String title, String start, String end) {
        return json.writeValueAsString(Map.of("title", title, "startsAt", start, "endsAt", end));
    }

    private String version(String body, int version) {
        return body.substring(0, body.length() - 1) + ",\"expectedVersion\":" + version + "}";
    }

    private ConfigurableApplicationContext start(BookingSubmissionHttpTests.MutableClock clock, String zone) {
        return new SpringApplicationBuilder(CalendarApplication.class)
                .initializers(context -> context.getBeanFactory().registerSingleton("testClock", clock))
                .run("--server.port=0", "--calendar.initial.booking-enabled=true",
                        "--calendar.initial.time-zone=" + zone);
    }

    private JsonNode get(ConfigurableApplicationContext app, String path) throws Exception {
        var response = send(app, "GET", path, null, null);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        return json.readTree(response.body());
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
