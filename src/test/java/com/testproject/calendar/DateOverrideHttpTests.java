package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

class DateOverrideHttpTests {
    private final JsonMapper json = JsonMapper.builder().build();
    private static final String TYPE = "/api/owner/meeting-types/Consult";
    private static final String DATE = TYPE + "/date-overrides/2026-11-02";

    @Test
    void exceptionReplacesRatherThanAddsAndClosedExceptionDiffersFromDeletingAfterWeeklyChange() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            create(app, false);
            var token = get(app, "/api/public/owner/meeting-types/Consult").get("meetingType").get("conditionsToken");
            var response = send(app, "PUT", DATE, override(1, "20:10", "21:00", "20:30", "21:30",
                    "21:30", "22:00"), "save");
            assertEquals(200, response.statusCode(), response.body());
            var saved = json.readTree(response.body());
            assertEquals(2, saved.get("version").asInt());
            assertEquals("20:10", saved.get("dateOverrides").get(0).get("intervals").get(0).get("start").asString());
            assertEquals("22:00", saved.get("dateOverrides").get(0).get("intervals").get(0).get("end").asString());
            assertEquals(1, saved.get("dateOverrides").get(0).get("intervals").size());
            var slots = dateSlots(app, "2026-11-02");
            assertEquals("2026-11-02T20:15:00+03:00", slots.getFirst().get("startsAt").asString());
            assertFalse(slots.stream().anyMatch(slot -> slot.get("startsAt").asString().contains("T09:")));
            assertEquals(200, send(app, "PUT", DATE, override(2), "close").statusCode());
            assertTrue(dateSlots(app, "2026-11-02").isEmpty());
            assertEquals(1, get(app, TYPE).get("dateOverrides").size());
            assertEquals(200, send(app, "PUT", TYPE + "/weekly-hours",
                    "{\"expectedVersion\":3,\"days\":[{\"day\":\"MONDAY\",\"intervals\":["
                            + "{\"start\":\"12:00\",\"end\":\"13:00\"}]}]}", "weekly").statusCode());
            assertTrue(dateSlots(app, "2026-11-02").isEmpty());
            assertEquals(200, send(app, "DELETE", DATE, "{\"expectedVersion\":4}", "delete").statusCode());
            assertEquals(0, get(app, TYPE).get("dateOverrides").size());
            assertEquals("2026-11-02T12:00:00+03:00", dateSlots(app, "2026-11-02").getFirst()
                    .get("startsAt").asString());
            assertEquals(token, get(app, "/api/public/owner/meeting-types/Consult")
                    .get("meetingType").get("conditionsToken"));
            assertEquals(saved, get(app, "/api/owner/operations/save").get("meetingType"));
            assertEquals(saved, json.readTree(send(app, "PUT", DATE, override(1, "20:10", "21:00",
                    "20:30", "21:30", "21:30", "22:00"), "save").body()));
        }
    }

    @Test
    void savingAndDeletingRequireFreshAtomicConsentAndWeeklyImpactUsesEffectiveExceptions() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            create(app, true);
            var pending = book(app, "2026-11-02T09:00:00+03:00");
            var body = override(1);
            var before = get(app, TYPE);
            var warning = send(app, "PUT", DATE, body, "preview");
            assertEquals(409, warning.statusCode(), warning.body());
            assertEquals("no-store", warning.headers().firstValue("Cache-Control").orElseThrow());
            var error = json.readTree(warning.body());
            assertEquals("IMPACT_CONFIRMATION_REQUIRED", error.get("code").asString());
            assertEquals(1, error.get("affectedBookings").size());
            assertEquals(pending.get("id"), error.get("affectedBookings").get(0).get("id"));
            assertEquals(before, get(app, TYPE));
            assertFalse(warning.body().contains("resultUrl"));
            assertEquals(200, send(app, "POST", "/api/owner/bookings/" + pending.get("id").asString()
                    + "/confirm", "{\"expectedVersion\":1}", "confirm").statusCode());
            var refreshed = send(app, "PUT", DATE, consent(body, error), "stale-consent");
            assertEquals(409, refreshed.statusCode());
            var fresh = json.readTree(refreshed.body());
            assertEquals("CONFIRMED", fresh.get("affectedBookings").get(0).get("status").asString());
            assertNotEquals(error.get("confirmationToken"), fresh.get("confirmationToken"));
            assertEquals(before, get(app, TYPE));
            assertEquals(200, send(app, "PUT", DATE, consent(body, fresh), "agree").statusCode());
            // A weekly change beneath a closed exception must not re-warn this booking.
            assertEquals(200, send(app, "PUT", TYPE + "/weekly-hours",
                    "{\"expectedVersion\":2,\"days\":[]}", "close-week").statusCode());
            assertEquals(200, send(app, "PUT", DATE, override(3, "09:00", "10:00"), "reopen").statusCode());
            // Deleting an opening exception contracts availability back to the CURRENT closed week.
            var deletion = "{\"expectedVersion\":4}";
            var deleteWarning = send(app, "DELETE", DATE, deletion, "delete-preview");
            assertEquals(409, deleteWarning.statusCode(), deleteWarning.body());
            var deleteError = json.readTree(deleteWarning.body());
            assertEquals(1, deleteError.get("affectedBookings").size());
            // Neither another date nor PUT can use the DELETE consent.
            assertEquals(409, send(app, "PUT", DATE, consent(override(4), deleteError),
                    "wrong-operation").statusCode());
            assertEquals(200, send(app, "DELETE", DATE, consent(deletion, deleteError), "delete-agree").statusCode());
            assertEquals(0, get(app, TYPE).get("dateOverrides").size());
            assertEquals("CONFIRMED", get(app, "/api/owner/bookings/" + pending.get("id").asString())
                    .get("status").asString());
            assertEquals(error, get(app, "/api/owner/operations/preview").get("error"));
        }
    }

    @Test
    void malformedDatesIntervalsAndVersionsAreJournaledWithoutPartialWritesAndArchiveRejectsBothOperations()
            throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            create(app, false);
            var before = get(app, TYPE);
            for (var date : List.of("2026-02-29", "2026-11-31", "26-11-02", "2026-1-02", "2026-11-02T00:00")) {
                for (var method : List.of("PUT", "DELETE")) {
                    var key = UUID.randomUUID().toString();
                    var response = send(app, method, TYPE + "/date-overrides/" + date, override(1), key);
                    assertEquals(422, response.statusCode(), response.body());
                    assertEquals("date", json.readTree(response.body()).get("fieldErrors")
                            .get(0).get("field").asString());
                    assertEquals(json.readTree(response.body()), get(app, "/api/owner/operations/" + key).get("error"));
                    assertEquals(before, get(app, TYPE));
                }
            }
            for (var body : List.of(override(1, "24:00", "24:00"), override(1, "10:00", "10:00"),
                    override(1, "23:00", "01:00"), override(1, "9:00", "10:00"),
                    override(1, "09:60", "10:00"), override(1, "09:00:00", "10:00"),
                    "{\"expectedVersion\":1,\"intervals\":null}")) {
                var key = UUID.randomUUID().toString();
                var response = send(app, "PUT", DATE, body, key);
                assertEquals(422, response.statusCode(), response.body());
                assertTrue(json.readTree(response.body()).get("fieldErrors").get(0).get("field")
                        .asString().startsWith("intervals"), response.body());
                assertEquals(json.readTree(response.body()), get(app, "/api/owner/operations/" + key).get("error"));
                assertEquals(json.readTree(response.body()), json.readTree(send(app, "PUT", DATE, body, key).body()));
                assertEquals(before, get(app, TYPE));
            }
            for (var method : List.of("PUT", "DELETE")) {
                for (var body : List.of("{", "{\"expectedVersion\":1.5}", "{\"expectedVersion\":\"1\"}")) {
                    var key = UUID.randomUUID().toString();
                    var response = send(app, method, DATE, body, key);
                    assertEquals(400, response.statusCode(), response.body());
                    assertEquals(json.readTree(response.body()), get(app, "/api/owner/operations/" + key).get("error"));
                    assertEquals(409, send(app, method, DATE, body + " ", key).statusCode());
                    assertEquals(before, get(app, TYPE));
                }
                assertEquals(422, send(app, method, DATE, override(0), UUID.randomUUID().toString()).statusCode());
                assertEquals(409, send(app, method, DATE, override(2), UUID.randomUUID().toString()).statusCode());
                assertEquals(400, send(app, method, DATE, override(1), null).statusCode());
                assertEquals(400, send(app, method, DATE, override(1), "bad.key").statusCode());
                assertEquals(404, send(app, method, DATE.replace("Consult", "Missing"), override(1),
                        UUID.randomUUID().toString()).statusCode());
            }
            assertEquals(200, send(app, "POST", TYPE + "/archive", "{\"expectedVersion\":1}", "archive").statusCode());
            var archived = get(app, TYPE);
            for (var method : List.of("PUT", "DELETE")) {
                var response = send(app, method, DATE, override(2), UUID.randomUUID().toString());
                assertEquals(409, response.statusCode(), response.body());
                assertEquals("ACTION_NOT_ALLOWED", json.readTree(response.body()).get("code").asString());
                assertEquals(archived, get(app, TYPE));
            }
        }
    }

    @Test
    void openingClosedDayAndAdjacentDateOverridesUseTypeZoneMidnightGridAndActualDstDuration() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        clock.set("2026-10-24T09:00:00Z");
        try (var app = start(clock, "Europe/Berlin")) {
            create(app, false);
            var sunday = TYPE + "/date-overrides/2026-10-25";
            assertTrue(dateSlots(app, "2026-10-25").isEmpty());
            assertEquals(200, send(app, "PUT", sunday, override(1, "02:15", "02:45"), "fall").statusCode());
            var repeated = dateSlots(app, "2026-10-25");
            assertEquals(2, repeated.size());
            assertEquals("2026-10-25T02:15:00+02:00", repeated.get(0).get("startsAt").asString());
            assertEquals("2026-10-25T02:45:00+02:00", repeated.get(0).get("endsAt").asString());
            assertEquals("2026-10-25T02:15:00+01:00", repeated.get(1).get("startsAt").asString());
            assertEquals("2026-10-25T02:45:00+01:00", repeated.get(1).get("endsAt").asString());
            assertEquals(200, send(app, "PUT", sunday, override(2, "23:00", "24:00"), "night").statusCode());
            assertFalse(dateSlots(app, "2026-10-25").stream().anyMatch(slot ->
                    slot.get("startsAt").asString().contains("T23:45")));
            assertEquals(200, send(app, "PUT", TYPE + "/date-overrides/2026-10-26",
                    override(3, "00:00", "01:00"), "adjacent").statusCode());
            assertTrue(dateSlots(app, "2026-10-25").stream().anyMatch(slot ->
                    slot.get("startsAt").asString().equals("2026-10-25T23:45:00+01:00")
                            && slot.get("endsAt").asString().equals("2026-10-26T00:15:00+01:00")));
            // Date is local to the type, not UTC: this starts on October 25 UTC.
            assertEquals(201, submit(app, prepare(app, "2026-10-25T23:00:00Z")).statusCode());
        }
        clock.set("2026-03-28T09:00:00Z");
        try (var app = start(clock, "Europe/Berlin")) {
            create(app, false);
            assertEquals(200, send(app, "PUT", TYPE + "/date-overrides/2026-03-29",
                    override(1, "01:45", "03:15"), "spring").statusCode());
            var spring = dateSlots(app, "2026-03-29");
            assertEquals(1, spring.size());
            assertEquals("2026-03-29T01:45:00+01:00", spring.getFirst().get("startsAt").asString());
            assertEquals("2026-03-29T03:15:00+02:00", spring.getFirst().get("endsAt").asString());
        }
    }

    @Test
    void closedPendingRemainsOccupancyAndCannotConfirmWhileUnchangedGuestAttemptStillSubmits() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        try (var app = start(clock, "Europe/Moscow")) {
            create(app, true);
            var pending = book(app, "2026-11-02T09:00:00+03:00");
            var valid = prepare(app, "2026-11-02T10:00:00+03:00");
            var closed = prepare(app, "2026-11-02T11:00:00+03:00");
            var body = override(1, "10:00", "10:30");
            var warning = json.readTree(send(app, "PUT", DATE, body, "preview").body());
            assertEquals(200, send(app, "PUT", DATE, consent(body, warning), "agree").statusCode());
            assertEquals(pending, get(app, "/api/owner/bookings/" + pending.get("id").asString()));
            var confirm = send(app, "POST", "/api/owner/bookings/" + pending.get("id").asString() + "/confirm",
                    "{\"expectedVersion\":1}", "confirm");
            assertEquals(409, confirm.statusCode());
            assertEquals("SLOT_UNAVAILABLE", json.readTree(confirm.body()).get("code").asString());
            assertEquals(201, submit(app, valid).statusCode());
            var refused = submit(app, closed);
            assertEquals(409, refused.statusCode());
            assertEquals("SLOT_UNAVAILABLE", json.readTree(refused.body()).get("code").asString());
            assertEquals(json.readTree(refused.body()), json.readTree(submit(app, closed).body()));
            assertEquals(201, send(app, "POST", "/api/owner/meeting-types",
                    "{\"id\":\"Other\",\"title\":\"Other\",\"description\":\"Another long meeting description\","
                            + "\"durationMinutes\":30}", "other").statusCode());
            var otherSlots = get(app, "/api/public/owner/meeting-types/Other/slots").get("items");
            assertFalse(otherSlots.valueStream().anyMatch(slot -> slot.get("startsAt").asString()
                    .equals("2026-11-02T09:00:00+03:00")));
            clock.set("2026-11-02T06:00:00Z");
            assertEquals("REJECTED", get(app, "/api/owner/bookings/" + pending.get("id").asString())
                    .get("status").asString());
        }
    }

    @Test
    void consentRechecksChangedSetAndExcludesAlreadyInvalidStartedAndTerminalRecords() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        try (var app = start(clock, "Europe/Moscow")) {
            create(app, true);
            var started = book(app, "2026-11-02T09:00:00+03:00");
            var terminal = book(app, "2026-11-02T09:30:00+03:00");
            var future = book(app, "2026-11-02T10:00:00+03:00");
            assertEquals(200, send(app, "POST", "/api/owner/bookings/" + terminal.get("id").asString() + "/reject",
                    "{\"expectedVersion\":1}", "reject").statusCode());
            var body = override(1);
            var first = json.readTree(send(app, "PUT", DATE, body, "preview").body());
            assertEquals(2, first.get("affectedBookings").size());
            book(app, "2026-11-02T11:00:00+03:00");
            clock.set("2026-11-02T06:00:00Z");
            var stale = send(app, "PUT", DATE, consent(body, first), "changed-set");
            assertEquals(409, stale.statusCode());
            var fresh = json.readTree(stale.body());
            assertEquals(2, fresh.get("affectedBookings").size());
            assertEquals(future.get("id"), fresh.get("affectedBookings").get(0).get("id"));
            assertEquals(1, get(app, TYPE).get("version").asInt());
            assertEquals(200, send(app, "PUT", DATE, consent(body, fresh), "agree").statusCode());
            // An edit unrelated to already-invalid bookings must not warn again.
            assertEquals(200, send(app, "PUT", DATE, override(2, "12:00", "13:00"), "unrelated").statusCode());
            assertEquals("REJECTED", get(app, "/api/owner/bookings/" + started.get("id").asString())
                    .get("status").asString());
        }
    }

    @Test
    void concurrentSubmissionAndDateClosureAndConcurrentConsentNeverLeavePartialUnacknowledgedChange()
            throws Exception {
        for (boolean consent : List.of(false, true)) {
            try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
                create(app, true);
                if (consent) {
                    book(app, "2026-11-02T09:00:00+03:00");
                }
                var prepared = prepare(app, "2026-11-02T10:00:00+03:00");
                var body = override(1);
                if (consent) {
                    body = consent(body, json.readTree(send(app, "PUT", DATE, body, "preview").body()));
                }
                var proposed = body;
                var gate = new java.util.concurrent.CyclicBarrier(3);
                try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                    var submit = executor.submit(() -> {
                        gate.await();
                        return submit(app, prepared);
                    });
                    var save = executor.submit(() -> {
                        gate.await();
                        return send(app, "PUT", DATE, proposed, "race");
                    });
                    gate.await();
                    var booked = submit.get();
                    var edited = save.get();
                    if (booked.statusCode() == 201) {
                        assertEquals(409, edited.statusCode(), edited.body());
                        assertEquals(consent ? 2 : 1, json.readTree(edited.body()).get("affectedBookings").size());
                        assertEquals(1, get(app, TYPE).get("version").asInt());
                        assertEquals(0, get(app, TYPE).get("dateOverrides").size());
                    } else {
                        assertEquals(409, booked.statusCode());
                        assertEquals("SLOT_UNAVAILABLE", json.readTree(booked.body()).get("code").asString());
                        assertEquals(200, edited.statusCode(), edited.body());
                        assertEquals(2, get(app, TYPE).get("version").asInt());
                        assertEquals(consent ? 1 : 0,
                                get(app, "/api/owner/bookings?view=upcoming").get("items").size());
                    }
                }
            }
        }
    }

    @Test
    void weeklyImpactHonorsOpeningOverrideAndDateTokensBindCanonicalIntervalsAndDate() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            create(app, true);
            book(app, "2026-11-02T09:00:00+03:00");
            book(app, "2026-11-03T09:00:00+03:00");
            assertEquals(200, send(app, "PUT", DATE, override(1, "09:00", "10:00"), "override").statusCode());
            // Monday's underlying hours close, but the override keeps its booking valid.
            // Tuesday remains weekly and should be the ONLY impact in the preview.
            var body = "{\"expectedVersion\":2,\"days\":[]}";
            var weeklyWarning = send(app, "PUT", TYPE + "/weekly-hours", body, "weekly-preview");
            assertEquals(409, weeklyWarning.statusCode());
            var warning = json.readTree(weeklyWarning.body());
            assertEquals(1, warning.get("affectedBookings").size());
            assertEquals(java.time.Instant.parse("2026-11-03T06:00:00Z"),
                    java.time.OffsetDateTime.parse(warning.get("affectedBookings").get(0)
                            .get("startsAt").asString()).toInstant());
            assertEquals(200, send(app, "PUT", TYPE + "/weekly-hours",
                    consent(body, warning), "weekly-agree").statusCode());
            var canonical = override(3, "10:00", "11:00");
            var preview = json.readTree(send(app, "PUT", DATE, canonical, "preview").body());
            assertEquals(1, preview.get("affectedBookings").size());
            // Open Tuesday first, so closing a DIFFERENT date also has genuine impact.
            assertEquals(200, send(app, "PUT", TYPE + "/date-overrides/2026-11-03",
                    override(3, "09:00", "10:00"), "open-tuesday").statusCode());
            assertEquals(409, send(app, "PUT", DATE, consent(canonical, preview), "stale-version").statusCode());
            canonical = override(4, "10:00", "11:00");
            preview = json.readTree(send(app, "PUT", DATE, canonical, "fresh-preview").body());
            var wrongDate = send(app, "PUT", TYPE + "/date-overrides/2026-11-03",
                    consent(canonical, preview), "wrong-date");
            assertEquals(409, wrongDate.statusCode());
            assertEquals("IMPACT_CONFIRMATION_REQUIRED", json.readTree(wrongDate.body()).get("code").asString());
            assertNotEquals(preview.get("confirmationToken"), json.readTree(wrongDate.body()).get("confirmationToken"));
            // Same canonical proposal accepts the token even if the submitted intervals overlap/touch.
            assertEquals(200, send(app, "PUT", DATE, consent(override(4, "10:30", "11:00",
                    "10:00", "10:30", "10:15", "10:45"), preview), "canonical-agree").statusCode());
            assertEquals(1, get(app, TYPE).get("dateOverrides").get(0).get("intervals").size());
        }
    }

    @Test
    void parallelOwnerCommandsHaveOneVersionWinnerAndIdenticalConcurrentRetriesHaveOneOutcome() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            create(app, false);
            var gate = new java.util.concurrent.CyclicBarrier(3);
            try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var put = executor.submit(() -> {
                    gate.await();
                    return send(app, "PUT", DATE, override(1), "put");
                });
                var delete = executor.submit(() -> {
                    gate.await();
                    return send(app, "DELETE", DATE, "{\"expectedVersion\":1}", "delete");
                });
                gate.await();
                var a = put.get();
                var b = delete.get();
                assertEquals(List.of(200, 409),
                        java.util.stream.Stream.of(a.statusCode(), b.statusCode()).sorted().toList());
                assertEquals(2, get(app, TYPE).get("version").asInt());
                assertEquals(json.readTree(a.body()), get(app, "/api/owner/operations/put")
                        .get(a.statusCode() == 200 ? "meetingType" : "error"));
                assertEquals(json.readTree(b.body()), get(app, "/api/owner/operations/delete")
                        .get(b.statusCode() == 200 ? "meetingType" : "error"));
            }
            var retryGate = new java.util.concurrent.CyclicBarrier(3);
            try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var calls = new java.util.ArrayList<java.util.concurrent.Future<HttpResponse<String>>>();
                for (int i = 0; i < 2; i++) {
                    calls.add(executor.submit(() -> {
                        retryGate.await();
                        return send(app, "PUT", DATE, override(2, "00:00", "24:00"), "same");
                    }));
                }
                retryGate.await();
                var first = calls.getFirst().get();
                var second = calls.getLast().get();
                assertEquals(200, first.statusCode(), first.body());
                assertEquals(first.body(), second.body());
                assertEquals(3, get(app, TYPE).get("version").asInt());
                assertEquals(1, get(app, TYPE).get("dateOverrides").size());
                assertEquals(409, send(app, "DELETE", DATE, "{\"expectedVersion\":2}", "same").statusCode());
            }
        }
    }

    @Test
    void concurrentPendingConfirmationAndConsentRequireFreshWarningOrRejectClosedConfirmation() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            create(app, true);
            var pending = book(app, "2026-11-02T09:00:00+03:00");
            var body = override(1);
            var warning = json.readTree(send(app, "PUT", DATE, body, "preview").body());
            var gate = new java.util.concurrent.CyclicBarrier(3);
            try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var edit = executor.submit(() -> {
                    gate.await();
                    return send(app, "PUT", DATE, consent(body, warning), "consent");
                });
                var confirmation = executor.submit(() -> {
                    gate.await();
                    return send(app, "POST", "/api/owner/bookings/" + pending.get("id").asString() + "/confirm",
                            "{\"expectedVersion\":1}", "confirm");
                });
                gate.await();
                var saved = edit.get();
                var confirmed = confirmation.get();
                var current = get(app, "/api/owner/bookings/" + pending.get("id").asString());
                if (saved.statusCode() == 200) {
                    assertEquals(409, confirmed.statusCode(), confirmed.body());
                    assertEquals("SLOT_UNAVAILABLE", json.readTree(confirmed.body()).get("code").asString());
                    assertEquals(pending, current);
                } else {
                    assertEquals(409, saved.statusCode(), saved.body());
                    assertEquals(200, confirmed.statusCode(), confirmed.body());
                    assertEquals("CONFIRMED", json.readTree(saved.body()).get("affectedBookings")
                            .get(0).get("status").asString());
                    assertEquals(1, get(app, TYPE).get("version").asInt());
                    assertEquals(0, get(app, TYPE).get("dateOverrides").size());
                    assertEquals("CONFIRMED", current.get("status").asString());
                }
            }
        }
    }

    @Test
    void exceptionsHaveNoInventedBookingWindowBoundsAndAffectOnlyTheirType() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            create(app, false);
            assertEquals(201, send(app, "POST", "/api/owner/meeting-types",
                    "{\"id\":\"Other\",\"title\":\"Other\",\"description\":\"Another long meeting description\","
                            + "\"durationMinutes\":30}", "other").statusCode());
            assertEquals(200, send(app, "PUT", DATE, override(1), "closed").statusCode());
            assertTrue(dateSlots(app, "2026-11-02").isEmpty());
            assertTrue(get(app, "/api/public/owner/meeting-types/Other/slots").get("items").valueStream()
                    .anyMatch(slot -> slot.get("startsAt").asString().startsWith("2026-11-02")));
            assertEquals(200, send(app, "PUT", TYPE + "/date-overrides/2020-01-01", override(2), "past").statusCode());
            assertEquals(200, send(app, "PUT", TYPE + "/date-overrides/9999-12-31",
                    override(3, "00:00", "24:00"), "future").statusCode());
            assertEquals(3, get(app, TYPE).get("dateOverrides").size());
            assertEquals(0, get(app, "/api/owner/meeting-types/Other").get("dateOverrides").size());
        }
    }

    private String consent(String body, JsonNode error) {
        return body.substring(0, body.length() - 1) + ",\"confirmationToken\":\""
                + error.get("confirmationToken").asString() + "\"}";
    }

    @Test
    void malformedPutAndDeleteWithSamePathAndBodyCannotShareIdempotencyKey() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            create(app, false);
            var response = send(app, "PUT", DATE, "{", "malformed");
            assertEquals(400, response.statusCode());
            assertEquals(409, send(app, "DELETE", DATE, "{", "malformed").statusCode());
            assertEquals(json.readTree(response.body()), get(app, "/api/owner/operations/malformed").get("error"));
            assertEquals(1, get(app, TYPE).get("version").asInt());
        }
    }

    private JsonNode prepare(ConfigurableApplicationContext app, String time) throws Exception {
        var conditions = get(app, "/api/public/owner/meeting-types/Consult").get("meetingType");
        var response = send(app, "POST", "/api/public/owner/booking-attempts", json.writeValueAsString(Map.of(
                "meetingTypeId", "Consult", "conditionsToken", conditions.get("conditionsToken").asString(),
                "startsAt", time, "guestName", "Гость", "guestEmail", "guest@example.com")), null);
        assertEquals(201, response.statusCode(), response.body());
        return json.readTree(response.body());
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

    private JsonNode book(ConfigurableApplicationContext app, String time) throws Exception {
        var response = submit(app, prepare(app, time));
        assertEquals(201, response.statusCode(), response.body());
        return json.readTree(response.body()).get("booking");
    }

    private List<JsonNode> dateSlots(ConfigurableApplicationContext app, String date) throws Exception {
        return get(app, "/api/public/owner/meeting-types/Consult/slots").get("items").valueStream()
                .filter(slot -> slot.get("startsAt").asString().startsWith(date)).toList();
    }

    private String override(int version, String... bounds) {
        var intervals = new java.util.ArrayList<Map<String, String>>();
        for (int i = 0; i < bounds.length; i += 2) {
            intervals.add(Map.of("start", bounds[i], "end", bounds[i + 1]));
        }
        return json.writeValueAsString(Map.of("expectedVersion", version, "intervals", intervals));
    }

    private void create(ConfigurableApplicationContext app, boolean approval) throws Exception {
        var response = send(app, "POST", "/api/owner/meeting-types", json.writeValueAsString(Map.of(
                "id", "Consult", "title", "Консультация", "description", "Достаточно длинное описание встречи",
                "durationMinutes", 30, "requiresApproval", approval)), "create");
        assertEquals(201, response.statusCode(), response.body());
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
