package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

class OwnerTimeZoneHttpTests {
    private final JsonMapper json = JsonMapper.builder().build();
    private static final String ZONE = "/api/owner/time-zone";
    private static final String TYPES = "/api/owner/meeting-types";

    @Test
    void ownerOnlyChangeRetainsOldTypesAndAllActiveTranslationPreservesWallRulesAndArchive() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            createType(app, "Old", false);
            createType(app, "Archived", false);
            assertEquals(200, send(app, "POST", TYPES + "/Archived/archive",
                    "{\"expectedVersion\":1}", "archive").statusCode());
            assertEquals(200, send(app, "PUT", TYPES + "/Old/date-overrides/2026-11-02",
                    "{\"expectedVersion\":1,\"intervals\":[{\"start\":\"10:00\",\"end\":\"11:00\"}]}",
                    "exception").statusCode());
            var old = get(app, TYPES + "/Old");
            var archived = get(app, TYPES + "/Archived");
            var response = send(app, "PUT", ZONE, zone("America/New_York", false, 1), "owner-only");
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("America/New_York", json.readTree(response.body()).get("timeZone").asString());
            assertEquals(old, get(app, TYPES + "/Old"));
            createType(app, "New", false);
            assertEquals("America/New_York", get(app, TYPES + "/New").get("timeZone").asString());
            var changed = send(app, "PUT", ZONE, zone("Europe/Berlin", true, 2), "all");
            assertEquals(200, changed.statusCode(), changed.body());
            var translated = get(app, TYPES + "/Old");
            assertEquals("Europe/Berlin", translated.get("timeZone").asString());
            assertEquals(old.get("version").asInt() + 1, translated.get("version").asInt());
            assertEquals(old.get("days"), translated.get("days"));
            assertEquals(old.get("dateOverrides"), translated.get("dateOverrides"));
            assertEquals(archived, get(app, TYPES + "/Archived"));
            assertEquals("Europe/Berlin", get(app, TYPES + "/New").get("timeZone").asString());
            assertEquals(json.readTree(response.body()), get(app, "/api/owner/operations/owner-only").get("profile"));
            assertEquals(json.readTree(response.body()),
                    json.readTree(send(app, "PUT", ZONE, zone("America/New_York", false, 1), "owner-only").body()));
        }
    }

    private String zone(String zone, boolean apply, long version) {
        return json.writeValueAsString(Map.of("timeZone", zone, "applyToActiveTypes", apply,
                "expectedVersion", version));
    }

    @Test
    void translationWarnsBeforeAtomicSaveAndStatusAndTypeChangesNeedFreshConsent() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            createType(app, "Old", true);
            var pending = book(app, "Old", "2026-11-02T09:00:00+03:00");
            var valid = prepare(app, "Old", "2026-11-02T12:00:00+03:00");
            var closed = prepare(app, "Old", "2026-11-02T09:30:00+03:00");
            var body = zone("Europe/Berlin", true, 1);
            var response = send(app, "PUT", ZONE, body, "preview");
            assertEquals(409, response.statusCode(), response.body());
            var warning = json.readTree(response.body());
            assertEquals("IMPACT_CONFIRMATION_REQUIRED", warning.get("code").asString());
            assertEquals(pending.get("id"), warning.get("affectedBookings").get(0).get("id"));
            assertEquals("Europe/Moscow", get(app, "/api/owner/profile").get("timeZone").asString());
            assertEquals(1, get(app, TYPES + "/Old").get("version").asInt());
            assertEquals(200, send(app, "POST", "/api/owner/bookings/" + pending.get("id").asString()
                    + "/confirm", "{\"expectedVersion\":1}", "confirm").statusCode());
            var stale = send(app, "PUT", ZONE, consent(body, warning), "stale-status");
            assertEquals(409, stale.statusCode(), stale.body());
            var fresh = json.readTree(stale.body());
            assertEquals("CONFIRMED", fresh.get("affectedBookings").get(0).get("status").asString());
            createType(app, "Added", false);
            var changed = send(app, "PUT", ZONE, consent(body, fresh), "stale-types");
            assertEquals(409, changed.statusCode(), changed.body());
            var newest = json.readTree(changed.body());
            assertEquals("IMPACT_CONFIRMATION_REQUIRED", newest.get("code").asString());
            assertEquals(200, send(app, "PUT", ZONE, consent(body, newest), "agree").statusCode());
            assertEquals("Europe/Berlin", get(app, TYPES + "/Added").get("timeZone").asString());
            var after = get(app, "/api/owner/bookings/" + pending.get("id").asString());
            assertEquals(pending.get("startsAt"), after.get("startsAt"));
            assertEquals("CONFIRMED", after.get("status").asString());
            assertEquals(warning, get(app, "/api/owner/operations/preview").get("error"));
            assertEquals(201, submit(app, valid).statusCode());
            assertEquals("SLOT_UNAVAILABLE", json.readTree(submit(app, closed).body()).get("code").asString());
        }
    }

    private String consent(String body, JsonNode warning) {
        return body.substring(0, body.length() - 1) + ",\"confirmationToken\":\""
                + warning.get("confirmationToken").asString() + "\"}";
    }

    @Test
    void ownerOnlyWindowChangeWarnsAndRechecksPreparedAttemptWithoutChangingTypeConditions() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        clock.set("2026-11-01T22:30:00Z");
        try (var app = start(clock, "America/New_York")) {
            createType(app, "Old", true);
            assertEquals(200, send(app, "PUT", TYPES + "/Old/date-overrides/2026-11-02",
                    "{\"expectedVersion\":1,\"intervals\":[{\"start\":\"00:00\",\"end\":\"24:00\"}]}",
                    "open").statusCode());
            var pending = book(app, "Old", "2026-11-02T00:00:00-05:00");
            var attempt = prepare(app, "Old", "2026-11-02T01:00:00-05:00");
            var currentType = get(app, TYPES + "/Old");
            var conditions = get(app, "/api/public/owner/meeting-types/Old").get("meetingType").get("conditionsToken");
            var body = zone("Asia/Tokyo", false, 1);
            var response = send(app, "PUT", ZONE, body, "preview");
            assertEquals(409, response.statusCode(), response.body());
            var warning = json.readTree(response.body());
            assertEquals(pending.get("id"), warning.get("affectedBookings").get(0).get("id"));
            assertEquals(200, send(app, "PUT", ZONE, consent(body, warning), "agree").statusCode());
            assertEquals(currentType, get(app, TYPES + "/Old"));
            var slots = get(app, "/api/public/owner/meeting-types/Old/slots");
            assertEquals("2026-11-03T00:00:00+09:00", slots.get("windowStart").asString());
            assertEquals("2026-11-17T00:00:00+09:00", slots.get("windowEnd").asString());
            assertEquals("America/New_York", slots.get("meetingTypeTimeZone").asString());
            assertEquals(conditions, slots.get("conditionsToken"));
            assertEquals("OUTSIDE_BOOKING_WINDOW", json.readTree(submit(app, attempt).body()).get("code").asString());
            assertEquals("PENDING", get(app, "/api/owner/bookings/" + pending.get("id").asString())
                    .get("status").asString());
        }
    }

    @Test
    void savedVacationBoundsSurviveBothModesAndEditingOldVacationRetainsItsZone() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            createType(app, "Old", false);
            // An owner-only change makes a genuine cross-zone, partial type-local-day vacation possible.
            assertEquals(200, send(app, "PUT", ZONE, zone("America/New_York", false, 1), "owner").statusCode());
            var vacationResponse = send(app, "POST", "/api/owner/out-of-office",
                    "{\"startDate\":\"2026-11-02\",\"endDate\":\"2026-11-02\"}", "vacation");
            assertEquals(201, vacationResponse.statusCode(), vacationResponse.body());
            var vacation = json.readTree(vacationResponse.body());
            for (int i = 0; i < 2; i++) {
                assertEquals(200, send(app, "PUT", TYPES + "/Old/date-overrides/2026-11-0" + (2 + i),
                        "{\"expectedVersion\":" + (i + 1)
                                + ",\"intervals\":[{\"start\":\"00:00\",\"end\":\"24:00\"}]}",
                        "date-" + i).statusCode());
            }
            var before = get(app, "/api/public/owner/meeting-types/Old/slots").get("items");
            assertTrue(hasStart(before, "2026-11-02T07:30:00+03:00"));
            assertFalse(hasStart(before, "2026-11-02T07:45:00+03:00"));
            assertFalse(hasStart(before, "2026-11-02T08:00:00+03:00"));
            assertFalse(hasStart(before, "2026-11-03T07:45:00+03:00"));
            assertTrue(hasStart(before, "2026-11-03T08:00:00+03:00"));
            assertEquals(200, send(app, "PUT", ZONE, zone("Europe/Berlin", true, 2), "all").statusCode());
            assertEquals(vacation, get(app, "/api/owner/out-of-office").get("items").get(0));
            var translated = get(app, "/api/public/owner/meeting-types/Old/slots").get("items");
            assertTrue(hasStart(translated, "2026-11-02T05:30:00+01:00"));
            assertFalse(hasStart(translated, "2026-11-02T05:45:00+01:00"));
            assertTrue(hasStart(translated, "2026-11-03T06:00:00+01:00"));
            var edited = send(app, "PUT", "/api/owner/out-of-office/" + vacation.get("id").asString(),
                    "{\"startDate\":\"2026-11-03\",\"endDate\":\"2026-11-03\",\"expectedVersion\":1}", "edit-vacation");
            assertEquals(200, edited.statusCode(), edited.body());
            assertEquals("America/New_York", json.readTree(edited.body()).get("timeZone").asString());
            var after = get(app, "/api/public/owner/meeting-types/Old/slots").get("items");
            assertTrue(hasStart(after, "2026-11-03T05:30:00+01:00"));
            assertFalse(hasStart(after, "2026-11-03T05:45:00+01:00"));
            assertEquals(vacation, get(app, "/api/owner/operations/vacation").get("outOfOffice"));
            var future = send(app, "POST", "/api/owner/out-of-office",
                    "{\"startDate\":\"2026-11-10\",\"endDate\":\"2026-11-10\"}", "new-vacation");
            assertEquals("Europe/Berlin", json.readTree(future.body()).get("timeZone").asString());
        }
    }

    private boolean hasStart(JsonNode slots, String time) {
        return slots.valueStream().anyMatch(slot -> slot.get("startsAt").asString().equals(time));
    }

    @Test
    void blocksKeepActualNanosecondsAndNewManualInputsUseCurrentZoneGapAndFoldRules() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            var created = send(app, "POST", "/api/owner/time-blocks",
                    "{\"title\":\"Личное\",\"startsAt\":\"2026-10-25T04:30:00.123456789+03:00\","
                            + "\"endsAt\":\"2026-10-25T05:00:00.987654321+03:00\"}", "block");
            assertEquals(201, created.statusCode(), created.body());
            var block = json.readTree(created.body()).get("timeBlock");
            assertEquals(200, send(app, "PUT", ZONE, zone("Europe/Berlin", true, 1), "zone").statusCode());
            var calendar = get(app, "/api/owner/calendar?from=2026-10-25T00:00:00Z&to=2026-10-26T00:00:00Z");
            assertEquals(block, calendar.get("timeBlocks").get(0));
            var edited = send(app, "PUT", "/api/owner/time-blocks/" + block.get("id").asString(),
                    "{\"title\":\"Другое название\",\"startsAt\":\"2026-10-25T02:30:00.123456789+01:00\","
                            + "\"endsAt\":\"2026-10-25T03:00:00.987654321+01:00\",\"expectedVersion\":1}", "edit");
            assertEquals(200, edited.statusCode(), edited.body());
            var after = json.readTree(edited.body()).get("timeBlock");
            assertEquals(java.time.OffsetDateTime.parse(block.get("startsAt").asString()).toInstant(),
                    java.time.OffsetDateTime.parse(after.get("startsAt").asString()).toInstant());
            assertEquals(java.time.OffsetDateTime.parse(block.get("endsAt").asString()).toInstant(),
                    java.time.OffsetDateTime.parse(after.get("endsAt").asString()).toInstant());
            for (var start : new String[]{"2026-03-29T02:30:00+01:00", "2026-10-25T02:15:00+03:00"}) {
                var invalid = send(app, "POST", "/api/owner/time-blocks", json.writeValueAsString(Map.of(
                        "title", "Неверно", "startsAt", start, "endsAt", "2026-10-26T12:00:00+01:00")),
                        java.util.UUID.randomUUID().toString());
                assertEquals(422, invalid.statusCode(), invalid.body());
            }
            for (var offset : new String[]{"+02:00", "+01:00"}) {
                var fold = send(app, "POST", "/api/owner/time-blocks", json.writeValueAsString(Map.of(
                        "title", "Повтор", "startsAt", "2026-10-25T02:00:00" + offset,
                        "endsAt", "2026-10-25T02:15:00" + offset)), "fold-" + offset.charAt(2));
                assertEquals(201, fold.statusCode(), fold.body());
            }
            assertEquals(block, get(app, "/api/owner/operations/block").get("timeBlockResult").get("timeBlock"));
        }
    }

    @Test
    void translatedHoursApplyGapAndBothFoldOffsetsAndOwnerWindowSpansDstCalendarDays() throws Exception {
        for (var now : new String[]{"2026-03-28T09:00:00Z", "2026-10-24T09:00:00Z"}) {
            var clock = new BookingSubmissionHttpTests.MutableClock();
            clock.set(now);
            try (var app = start(clock, "Europe/Moscow")) {
                createType(app, "Old", false);
                var day = now.startsWith("2026-03") ? "2026-03-29" : "2026-10-25";
                assertEquals(200, send(app, "PUT", TYPES + "/Old/date-overrides/" + day,
                        "{\"expectedVersion\":1,\"intervals\":[{\"start\":\"02:00\",\"end\":\"03:30\"}]}",
                        "date").statusCode());
                assertEquals(200, send(app, "PUT", ZONE, zone("Europe/Berlin", true, 1), "translate").statusCode());
                var slots = get(app, "/api/public/owner/meeting-types/Old/slots");
                if (day.startsWith("2026-03")) {
                    assertEquals("2026-03-29T00:00:00+01:00", slots.get("windowStart").asString());
                    assertEquals("2026-04-12T00:00:00+02:00", slots.get("windowEnd").asString());
                    assertFalse(slots.get("items").valueStream().anyMatch(slot ->
                            slot.get("startsAt").asString().startsWith(day + "T02:")));
                    assertTrue(hasStart(slots.get("items"), day + "T03:00:00+02:00"));
                } else {
                    assertEquals("2026-10-25T00:00:00+02:00", slots.get("windowStart").asString());
                    assertEquals("2026-11-08T00:00:00+01:00", slots.get("windowEnd").asString());
                    assertTrue(hasStart(slots.get("items"), day + "T02:00:00+02:00"));
                    assertTrue(hasStart(slots.get("items"), day + "T02:00:00+01:00"));
                }
            }
        }
    }

    @Test
    void concurrentProfileAndZoneSaveUseOneVersionAndConcurrentTypeEditCannotBePartiallyOverwritten() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            createType(app, "Old", false);
            createType(app, "Other", false);
            var results = race(() -> send(app, "PUT", ZONE, zone("Europe/Berlin", true, 1), "zone"),
                    () -> send(app, "PUT", "/api/owner/profile",
                            "{\"name\":\"Новое имя\",\"username\":\"new-owner\",\"expectedVersion\":1}", "profile"));
            assertEquals(java.util.Set.of(200, 409),
                    java.util.Set.of(results.get(0).statusCode(), results.get(1).statusCode()));
            var applied = results.get(0).statusCode() == 200;
            assertEquals(applied ? "Europe/Berlin" : "Europe/Moscow",
                    get(app, TYPES + "/Old").get("timeZone").asString());
            assertEquals(get(app, TYPES + "/Old").get("timeZone"), get(app, TYPES + "/Other").get("timeZone"));
            var current = get(app, TYPES + "/Old");
            var edit = "{\"title\":\"Новое название\",\"description\":\"Новое достаточно длинное описание встречи\","
                    + "\"durationMinutes\":30,\"requiresApproval\":false,\"expectedVersion\":"
                    + current.get("version").asInt() + "}";
            var competing = race(() -> send(app, "PUT", ZONE, zone("Asia/Tokyo", true, 2), "next-zone"),
                    () -> send(app, "PUT", TYPES + "/Old", edit, "type-edit"));
            assertEquals(200, competing.get(0).statusCode(), competing.get(0).body());
            assertTrue(competing.get(1).statusCode() == 200 || competing.get(1).statusCode() == 409);
            assertEquals("Asia/Tokyo", get(app, TYPES + "/Old").get("timeZone").asString());
            assertEquals("Asia/Tokyo", get(app, TYPES + "/Other").get("timeZone").asString());
            assertEquals(competing.get(1).statusCode() == 200 ? "Новое название" : "Old",
                    get(app, TYPES + "/Old").get("title").asString());
            assertEquals(json.readTree(competing.get(0).body()),
                    get(app, "/api/owner/operations/next-zone").get("profile"));
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

    @Test
    void concurrentSubmissionAndConfirmationSerializeWithTranslationAndJournalWithoutPartialSave() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            createType(app, "Old", false);
            createType(app, "Other", false);
            var attempt = prepare(app, "Old", "2026-11-02T09:00:00+03:00");
            var results = race(() -> send(app, "PUT", ZONE, zone("Europe/Berlin", true, 1), "zone"),
                    () -> submit(app, attempt));
            if (results.get(0).statusCode() == 200) {
                assertEquals(409, results.get(1).statusCode());
                assertEquals("SLOT_UNAVAILABLE", json.readTree(results.get(1).body()).get("code").asString());
                assertEquals("Europe/Berlin", get(app, TYPES + "/Other").get("timeZone").asString());
            } else {
                assertEquals(409, results.get(0).statusCode(), results.get(0).body());
                assertEquals(201, results.get(1).statusCode(), results.get(1).body());
                assertEquals("IMPACT_CONFIRMATION_REQUIRED",
                        json.readTree(results.get(0).body()).get("code").asString());
                assertEquals("Europe/Moscow", get(app, TYPES + "/Other").get("timeZone").asString());
            }
        }
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            createType(app, "Old", true);
            createType(app, "Other", false);
            var pending = book(app, "Old", "2026-11-02T09:00:00+03:00");
            var body = zone("Europe/Berlin", true, 1);
            var warning = json.readTree(send(app, "PUT", ZONE, body, "preview").body());
            var results = race(() -> send(app, "PUT", ZONE, consent(body, warning), "zone"),
                    () -> send(app, "POST", "/api/owner/bookings/" + pending.get("id").asString()
                            + "/confirm", "{\"expectedVersion\":1}", "confirm"));
            assertEquals(java.util.Set.of(200, 409),
                    java.util.Set.of(results.get(0).statusCode(), results.get(1).statusCode()));
            if (results.get(0).statusCode() == 200) {
                assertEquals("PENDING", get(app, "/api/owner/bookings/" + pending.get("id").asString())
                        .get("status").asString());
                assertEquals("Europe/Berlin", get(app, TYPES + "/Other").get("timeZone").asString());
            } else {
                assertEquals("CONFIRMED", json.readTree(results.get(0).body()).get("affectedBookings")
                        .get(0).get("status").asString());
                assertEquals("Europe/Moscow", get(app, TYPES + "/Other").get("timeZone").asString());
            }
            assertEquals(results.get(0).statusCode(), get(app, "/api/owner/operations/zone").get("statusCode").asInt());
        }
    }

    @Test
    void staleConsentBindsCanonicalProposalTypeEditAndArchiveEvenWhenImpactedSetIsUnchangedOrEmpty() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            createType(app, "Old", true);
            createType(app, "Other", false);
            book(app, "Old", "2026-11-02T09:00:00+03:00");
            var body = zone("Europe/Berlin", true, 1);
            var warning = json.readTree(send(app, "PUT", ZONE, body, "preview").body());
            assertEquals(200, send(app, "PUT", TYPES + "/Other/weekly-hours",
                    "{\"expectedVersion\":1,\"days\":[]}", "hours").statusCode());
            var edited = send(app, "PUT", ZONE, consent(body, warning), "after-edit");
            assertEquals(409, edited.statusCode(), edited.body());
            warning = json.readTree(edited.body());
            assertEquals(200, send(app, "POST", TYPES + "/Other/archive",
                    "{\"expectedVersion\":2}", "archive").statusCode());
            var archived = send(app, "PUT", ZONE, consent(body, warning), "after-archive");
            assertEquals(409, archived.statusCode(), archived.body());
            warning = json.readTree(archived.body());
            var off = zone("Europe/Berlin", false, 1);
            var changedToggle = send(app, "PUT", ZONE, consent(off, warning), "changed-toggle");
            assertEquals(409, changedToggle.statusCode(), changedToggle.body());
            var empty = json.readTree(changedToggle.body());
            assertEquals(0, empty.get("affectedBookings").size());
            assertEquals("Europe/Moscow", get(app, "/api/owner/profile").get("timeZone").asString());
            assertEquals(200, send(app, "PUT", ZONE, consent(off, empty), "agree-empty").statusCode());
            assertEquals("Europe/Moscow", get(app, TYPES + "/Old").get("timeZone").asString());
            assertEquals(3, get(app, TYPES + "/Other").get("version").asInt());
        }
    }

    @Test
    void impactExcludesAlreadyInvalidStartedPastAndTerminalBookingsAtExactClockBoundary() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        try (var app = start(clock, "Europe/Moscow")) {
            createType(app, "Old", true);
            var expired = book(app, "Old", "2026-11-02T09:00:00+03:00");
            var started = book(app, "Old", "2026-11-02T09:30:00+03:00");
            var terminal = book(app, "Old", "2026-11-02T10:00:00+03:00");
            book(app, "Old", "2026-11-03T09:00:00+03:00");
            var valid = book(app, "Old", "2026-11-03T09:30:00+03:00");
            assertEquals(200, send(app, "POST", "/api/owner/bookings/" + started.get("id").asString()
                    + "/confirm", "{\"expectedVersion\":1}", "confirm").statusCode());
            assertEquals(200, send(app, "POST", "/api/owner/bookings/" + terminal.get("id").asString()
                    + "/reject", "{\"expectedVersion\":1}", "reject").statusCode());
            var closeOne = "{\"expectedVersion\":1,\"intervals\":[{\"start\":\"09:30\",\"end\":\"18:00\"}]}";
            var closePath = TYPES + "/Old/date-overrides/2026-11-03";
            var warning = json.readTree(send(app, "PUT", closePath, closeOne, "close-preview").body());
            assertEquals(200, send(app, "PUT", closePath, consent(closeOne, warning), "close").statusCode());
            clock.set("2026-11-02T06:30:00Z");
            var response = send(app, "PUT", ZONE, zone("Europe/Berlin", true, 1), "zone");
            assertEquals(409, response.statusCode(), response.body());
            var affected = json.readTree(response.body()).get("affectedBookings");
            assertEquals(1, affected.size());
            assertEquals(valid.get("id"), affected.get(0).get("id"));
            assertEquals("REJECTED", get(app, "/api/owner/bookings/" + expired.get("id").asString())
                    .get("status").asString());
        }
    }

    @Test
    void closedPendingKeepsItsInstantAndOccupancyButCannotBeConfirmedAndGuestConditionsStaySemantic() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            createType(app, "Old", true);
            var pending = book(app, "Old", "2026-11-02T09:00:00+03:00");
            var body = zone("Europe/Berlin", true, 1);
            var warning = json.readTree(send(app, "PUT", ZONE, body, "preview").body());
            assertEquals(200, send(app, "PUT", ZONE, consent(body, warning), "agree").statusCode());
            var after = get(app, "/api/owner/bookings/" + pending.get("id").asString());
            assertEquals(pending.get("startsAt"), after.get("startsAt"));
            assertEquals(pending.get("endsAt"), after.get("endsAt"));
            assertEquals(pending.get("version"), after.get("version"));
            assertEquals(pending.get("status"), after.get("status"));
            assertEquals("Europe/Berlin", after.get("ownerTimeZone").asString());
            var confirm = send(app, "POST", "/api/owner/bookings/" + pending.get("id").asString()
                    + "/confirm", "{\"expectedVersion\":1}", "confirm");
            assertEquals(409, confirm.statusCode());
            assertEquals("SLOT_UNAVAILABLE", json.readTree(confirm.body()).get("code").asString());
            // Change back without moving the request: it still occupies the original instant.
            assertEquals(200, send(app, "PUT", ZONE, zone("Europe/Moscow", true, 2), "back").statusCode());
            assertFalse(hasStart(get(app, "/api/public/owner/meeting-types/Old/slots").get("items"),
                    "2026-11-02T09:00:00+03:00"));
        }
    }

    @Test
    void validationAndVersionFailuresAreImmutableAndCannotTranslateAnyType() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            createType(app, "Old", false);
            var old = get(app, TYPES + "/Old");
            for (var body : new String[]{
                    "{\"timeZone\":\"Europe/Berlin\",\"expectedVersion\":1}",
                    "{\"timeZone\":\"+03:00\",\"applyToActiveTypes\":true,\"expectedVersion\":1}",
                    "{\"timeZone\":\"Not/AZone\",\"applyToActiveTypes\":true,\"expectedVersion\":1}",
                    "{\"timeZone\":\"Europe/Berlin\",\"applyToActiveTypes\":null,\"expectedVersion\":1}",
                    "{\"timeZone\":\"Europe/Berlin\",\"applyToActiveTypes\":true,\"expectedVersion\":0}"}) {
                var key = java.util.UUID.randomUUID().toString();
                var response = send(app, "PUT", ZONE, body, key);
                assertEquals(422, response.statusCode(), response.body());
                assertEquals(json.readTree(response.body()), get(app, "/api/owner/operations/" + key).get("error"));
                assertEquals(409, send(app, "PUT", ZONE, zone("Europe/Berlin", true, 1), key).statusCode());
                assertEquals(old, get(app, TYPES + "/Old"));
            }
            var malformed = send(app, "PUT", ZONE,
                    "{\"timeZone\":\"Europe/Berlin\",\"applyToActiveTypes\":true,\"expectedVersion\":1.5}", "bad");
            assertEquals(400, malformed.statusCode(), malformed.body());
            assertEquals(json.readTree(malformed.body()), get(app, "/api/owner/operations/bad").get("error"));
            assertEquals(400, send(app, "PUT", ZONE, zone("Europe/Berlin", true, 1), null).statusCode());
            assertEquals(200, send(app, "PUT", "/api/owner/profile",
                    "{\"name\":\"Новое имя\",\"username\":\"new-owner\",\"expectedVersion\":1}",
                    "profile").statusCode());
            var conflict = send(app, "PUT", ZONE, zone("Europe/Berlin", true, 1), "stale");
            assertEquals(409, conflict.statusCode(), conflict.body());
            assertEquals("RESOURCE_CHANGED", json.readTree(conflict.body()).get("code").asString());
            assertEquals(old, get(app, TYPES + "/Old"));
            assertEquals("new-owner", get(app, "/api/owner/profile").get("username").asString());
            assertEquals(410, send(app, "GET", "/api/owner/operations/missing", null, null).statusCode());
        }
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
        return json.readTree(response.body()).get("booking");
    }

    private void createType(ConfigurableApplicationContext app, String id, boolean approval) throws Exception {
        var response = send(app, "POST", TYPES, json.writeValueAsString(Map.of(
                "id", id, "title", id, "description", "Достаточно длинное описание встречи",
                "durationMinutes", 30, "requiresApproval", approval)), "type-" + id);
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
