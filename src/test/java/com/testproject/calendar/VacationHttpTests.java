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

class VacationHttpTests {
    private final JsonMapper json = JsonMapper.builder().build();
    private static final String VACATIONS = "/api/owner/out-of-office";
    private static final String TYPE = "/api/owner/meeting-types/Consult";

    @Test
    void inclusiveGlobalVacationOverridesOpenDatesAndOverlapsOnlyRemoveTheirOwnRestriction() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            createType(app, "Consult", false);
            createType(app, "Other", false);
            var response = send(app, "POST", VACATIONS, dates("2026-11-02", "2026-11-03"), "create");
            assertEquals(201, response.statusCode(), response.body());
            var first = json.readTree(response.body());
            assertEquals("Europe/Moscow", first.get("timeZone").asString());
            assertEquals(1, first.get("version").asInt());
            assertEquals(1, get(app, VACATIONS).get("items").size());
            assertEquals(200, send(app, "PUT", TYPE + "/date-overrides/2026-11-02",
                    "{\"expectedVersion\":1,\"intervals\":[{\"start\":\"00:00\",\"end\":\"24:00\"}]}",
                    "open").statusCode());
            for (var type : new String[]{"Consult", "Other"}) {
                assertFalse(slots(app, type).valueStream().anyMatch(slot ->
                        slot.get("startsAt").asString().startsWith("2026-11-02")
                                || slot.get("startsAt").asString().startsWith("2026-11-03")));
                assertTrue(slots(app, type).valueStream().anyMatch(slot ->
                        slot.get("startsAt").asString().startsWith("2026-11-04")));
            }
            var second = json.readTree(send(app, "POST", VACATIONS,
                    dates("2026-11-03", "2026-11-04"), "second").body());
            assertEquals(204, send(app, "DELETE", VACATIONS + "/" + first.get("id").asString(),
                    "{\"expectedVersion\":1}", "delete-first").statusCode());
            assertTrue(slots(app, "Other").valueStream().anyMatch(slot ->
                    slot.get("startsAt").asString().startsWith("2026-11-02")));
            assertFalse(slots(app, "Other").valueStream().anyMatch(slot ->
                    slot.get("startsAt").asString().startsWith("2026-11-03")));
            assertEquals(204, send(app, "DELETE", VACATIONS + "/" + second.get("id").asString(),
                    "{\"expectedVersion\":1}", "delete-second").statusCode());
            assertTrue(slots(app, "Other").valueStream().anyMatch(slot ->
                    slot.get("startsAt").asString().startsWith("2026-11-03T09:00")));
            assertFalse(slots(app, "Other").valueStream().anyMatch(slot ->
                    slot.get("startsAt").asString().startsWith("2026-11-03T08:")));
            assertEquals(first, get(app, "/api/owner/operations/create").get("outOfOffice"));
            assertEquals(204, get(app, "/api/owner/operations/delete-first").get("statusCode").asInt());
            assertEquals(204, send(app, "DELETE", VACATIONS + "/" + first.get("id").asString(),
                    "{\"expectedVersion\":1}", "delete-first").statusCode());
        }
    }

    @Test
    void impactIsAtomicAcrossTypesAndStaleStatusRequiresNewConsentWhileClosedPendingStillOccupies() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            createType(app, "Consult", true);
            createType(app, "Other", false);
            var pending = book(app, "Consult", "2026-11-02T09:00:00+03:00");
            var confirmed = book(app, "Other", "2026-11-03T10:00:00+03:00");
            var attempt = prepare(app, "Consult", "2026-11-02T11:00:00+03:00");
            var valid = prepare(app, "Other", "2026-11-04T11:00:00+03:00");
            var body = dates("2026-11-02", "2026-11-03");
            var response = send(app, "POST", VACATIONS, body, "preview");
            assertEquals(409, response.statusCode(), response.body());
            var warning = json.readTree(response.body());
            assertEquals("IMPACT_CONFIRMATION_REQUIRED", warning.get("code").asString());
            assertEquals(2, warning.get("affectedBookings").size());
            assertEquals(0, get(app, VACATIONS).get("items").size());
            assertEquals(200, send(app, "POST", "/api/owner/bookings/" + pending.get("id").asString()
                    + "/confirm", "{\"expectedVersion\":1}", "confirm").statusCode());
            var stale = send(app, "POST", VACATIONS, consent(body, warning), "stale");
            assertEquals(409, stale.statusCode(), stale.body());
            var fresh = json.readTree(stale.body());
            assertEquals("CONFIRMED", fresh.get("affectedBookings").get(0).get("status").asString());
            assertEquals(0, get(app, VACATIONS).get("items").size());
            var saved = send(app, "POST", VACATIONS, consent(body, fresh), "agree");
            assertEquals(201, saved.statusCode(), saved.body());
            assertEquals(confirmed, get(app, "/api/owner/bookings/" + confirmed.get("id").asString()));
            assertEquals(warning, get(app, "/api/owner/operations/preview").get("error"));
            assertEquals(409, submit(app, attempt).statusCode());
            assertEquals("SLOT_UNAVAILABLE", json.readTree(submit(app, attempt).body()).get("code").asString());
            assertEquals(201, submit(app, valid).statusCode());
            // Already vacation-closed bookings must not cause false weekly/date warnings.
            assertEquals(200, send(app, "PUT", TYPE + "/weekly-hours",
                    "{\"expectedVersion\":1,\"days\":[]}", "week").statusCode());
            assertEquals(200, send(app, "PUT", TYPE + "/date-overrides/2026-11-02",
                    "{\"expectedVersion\":2,\"intervals\":[]}", "date").statusCode());
            var vacation = json.readTree(saved.body());
            assertEquals(204, send(app, "DELETE", VACATIONS + "/" + vacation.get("id").asString(),
                    "{\"expectedVersion\":1}", "delete").statusCode());
            assertFalse(slots(app, "Other").valueStream().anyMatch(slot ->
                    slot.get("startsAt").asString().equals("2026-11-03T10:00:00+03:00")));
        }
    }

    private String consent(String body, JsonNode warning) {
        return body.substring(0, body.length() - 1) + ",\"confirmationToken\":\""
                + warning.get("confirmationToken").asString() + "\"}";
    }

    @Test
    void midnightBoundariesFollowSavedZoneAcrossSpringAndFallAndEditingRetainsZone() throws Exception {
        for (var entry : Map.of("2026-03-28T09:00:00Z", "2026-03-29",
                "2026-10-24T09:00:00Z", "2026-10-25").entrySet()) {
            var clock = new BookingSubmissionHttpTests.MutableClock();
            clock.set(entry.getKey());
            try (var app = start(clock, "Europe/Berlin")) {
                createType(app, "Consult", false);
                var day = java.time.LocalDate.parse(entry.getValue());
                for (int i = 0; i < 3; i++) {
                    assertEquals(200, send(app, "PUT", TYPE + "/date-overrides/" + day.plusDays(i),
                            "{\"expectedVersion\":" + (i + 1)
                                    + ",\"intervals\":[{\"start\":\"00:00\",\"end\":\"24:00\"}]}",
                            "day-" + i).statusCode());
                }
                var before = slots(app, "Consult");
                assertEquals(day.getMonthValue() == 3 ? 92 : 100, before.valueStream().filter(slot ->
                        slot.get("startsAt").asString().startsWith(day.toString())).count());
                var response = send(app, "POST", VACATIONS, dates(day.toString(), day.toString()), "create");
                assertEquals(201, response.statusCode());
                var vacation = json.readTree(response.body());
                var after = slots(app, "Consult");
                assertFalse(after.valueStream().anyMatch(slot ->
                        slot.get("startsAt").asString().startsWith(day.toString())));
                var expected = day.plusDays(1) + (day.getMonthValue() == 3 ? "T00:00:00+02:00" : "T00:00:00+01:00");
                assertTrue(after.valueStream().anyMatch(slot -> slot.get("startsAt").asString().equals(expected)));
                var edit = send(app, "PUT", VACATIONS + "/" + vacation.get("id").asString(),
                        "{\"startDate\":\"" + day.plusDays(1) + "\",\"endDate\":\"" + day.plusDays(1)
                                + "\",\"expectedVersion\":1}", "edit");
                assertEquals(200, edit.statusCode(), edit.body());
                assertEquals("Europe/Berlin", json.readTree(edit.body()).get("timeZone").asString());
                var edited = slots(app, "Consult");
                assertTrue(edited.valueStream().anyMatch(slot -> slot.get("startsAt").asString()
                        .equals(day + "T23:30:00" + (day.getMonthValue() == 3 ? "+02:00" : "+01:00"))));
                assertFalse(edited.valueStream().anyMatch(slot -> slot.get("startsAt").asString()
                        .equals(day + "T23:45:00" + (day.getMonthValue() == 3 ? "+02:00" : "+01:00"))));
                assertEquals(204, send(app, "DELETE", VACATIONS + "/" + vacation.get("id").asString(),
                        "{\"expectedVersion\":2}", "delete").statusCode());
                assertEquals(before, slots(app, "Consult"));
                assertEquals(vacation, get(app, "/api/owner/operations/create").get("outOfOffice"));
            }
        }
    }

    @Test
    void invalidDatesVersionsAndMalformedCommandsAreRetainedWithoutPartialChangesOrKeyReuse() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            for (var body : new String[]{dates("2026-02-29", "2026-03-01"), dates("2026-11-03", "2026-11-02"),
                    dates("2026-1-01", "2026-11-02"), "{\"endDate\":\"2026-11-02\"}"}) {
                var key = java.util.UUID.randomUUID().toString();
                var response = send(app, "POST", VACATIONS, body, key);
                assertEquals(422, response.statusCode(), response.body());
                assertEquals(json.readTree(response.body()), get(app, "/api/owner/operations/" + key).get("error"));
                assertEquals(0, get(app, VACATIONS).get("items").size());
                assertEquals(409, send(app, "POST", VACATIONS, dates("2026-11-02", "2026-11-02"), key).statusCode());
            }
            var created = send(app, "POST", VACATIONS, dates("2020-01-01", "9999-12-31"), "create");
            assertEquals(201, created.statusCode());
            var saved = json.readTree(created.body());
            var path = VACATIONS + "/" + saved.get("id").asString();
            for (var method : new String[]{"PUT", "DELETE"}) {
                var body = "{\"startDate\":\"2026-11-02\",\"endDate\":\"2026-11-02\",\"expectedVersion\":2}";
                var conflict = send(app, method, path, body, method);
                assertEquals(409, conflict.statusCode(), conflict.body());
                assertEquals(saved, json.readTree(conflict.body()).get("currentOutOfOffice"));
                assertEquals(422, send(app, method, path, "{\"expectedVersion\":0}", method + "-zero").statusCode());
                assertEquals(404, send(app, method, VACATIONS + "/missing", "{\"expectedVersion\":1}",
                        method + "-missing").statusCode());
                var malformed = send(app, method, path, "{\"expectedVersion\":1.5}", method + "-bad");
                assertEquals(400, malformed.statusCode(), malformed.body());
                assertEquals(json.readTree(malformed.body()),
                        get(app, "/api/owner/operations/" + method + "-bad").get("error"));
                assertEquals(409, send(app, method.equals("PUT") ? "DELETE" : "PUT", path,
                        "{\"expectedVersion\":1.5}", method + "-bad").statusCode());
                assertEquals(400, send(app, method, path, "{\"expectedVersion\":1}", null).statusCode());
            }
            assertEquals(saved, get(app, VACATIONS).get("items").get(0));
            assertEquals(400, send(app, "POST", VACATIONS, "{}", "invalid.key").statusCode());
            assertEquals(410, send(app, "GET", "/api/owner/operations/unknown", null, null).statusCode());
            assertEquals(404, send(app, "GET", "/api/public/owner/out-of-office", null, null).statusCode());
        }
    }

    @Test
    void editPreviewBindsProposalAndVersionAndClosedPendingCannotConfirmEvenAfterArchive() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            createType(app, "Consult", true);
            var pending = book(app, "Consult", "2026-11-02T09:00:00+03:00");
            assertEquals(200, send(app, "POST", TYPE + "/archive", "{\"expectedVersion\":1}", "archive").statusCode());
            var created = json.readTree(send(app, "POST", VACATIONS,
                    dates("2026-11-03", "2026-11-03"), "create").body());
            var path = VACATIONS + "/" + created.get("id").asString();
            var body = "{\"startDate\":\"2026-11-02\",\"endDate\":\"2026-11-02\",\"expectedVersion\":1}";
            var preview = send(app, "PUT", path, body, "preview");
            assertEquals(409, preview.statusCode(), preview.body());
            var warning = json.readTree(preview.body());
            assertEquals(1, warning.get("affectedBookings").size());
            assertEquals(created, get(app, VACATIONS).get("items").get(0));
            assertEquals(409, send(app, "POST", VACATIONS, consent(dates("2026-11-02", "2026-11-02"), warning),
                    "wrong-operation").statusCode());
            assertEquals(200, send(app, "PUT", path, consent(body, warning), "agree").statusCode());
            assertEquals(409, send(app, "POST", "/api/owner/bookings/" + pending.get("id").asString() + "/confirm",
                    "{\"expectedVersion\":1}", "confirm").statusCode());
            assertEquals(pending, get(app, "/api/owner/bookings/" + pending.get("id").asString()));
            assertEquals(204, send(app, "DELETE", path, "{\"expectedVersion\":2}", "delete").statusCode());
            assertEquals(200, send(app, "POST", "/api/owner/bookings/" + pending.get("id").asString() + "/confirm",
                    "{\"expectedVersion\":1}", "confirm-after").statusCode());
        }
    }

    @Test
    void concurrentCreateAndBookingSerializeToWarningOrUnavailableWithoutPartialVacation() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            createType(app, "Consult", false);
            var attempt = prepare(app, "Consult", "2026-11-02T09:00:00+03:00");
            var results = race(() -> send(app, "POST", VACATIONS, dates("2026-11-02", "2026-11-02"), "race"),
                    () -> submit(app, attempt));
            if (results.get(0).statusCode() == 201) {
                assertEquals(409, results.get(1).statusCode());
                assertEquals(1, get(app, VACATIONS).get("items").size());
                assertEquals(0, get(app, "/api/owner/bookings?view=upcoming").get("items").size());
            } else {
                assertEquals(409, results.get(0).statusCode());
                assertEquals(201, results.get(1).statusCode());
                assertEquals(0, get(app, VACATIONS).get("items").size());
                assertEquals(1, json.readTree(results.get(0).body()).get("affectedBookings").size());
            }
        }
    }

    @Test
    void concurrentConsentAndOwnerConfirmationRequireFreshWarningOrRejectConfirmation() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            createType(app, "Consult", true);
            var pending = book(app, "Consult", "2026-11-02T09:00:00+03:00");
            var body = dates("2026-11-02", "2026-11-02");
            var warning = json.readTree(send(app, "POST", VACATIONS, body, "preview").body());
            var results = race(() -> send(app, "POST", VACATIONS, consent(body, warning), "agree"),
                    () -> send(app, "POST", "/api/owner/bookings/" + pending.get("id").asString() + "/confirm",
                            "{\"expectedVersion\":1}", "confirm"));
            if (results.get(0).statusCode() == 201) {
                assertEquals(409, results.get(1).statusCode());
                assertEquals(1, get(app, VACATIONS).get("items").size());
                assertEquals(pending, get(app, "/api/owner/bookings/" + pending.get("id").asString()));
            } else {
                assertEquals(409, results.get(0).statusCode());
                assertEquals(200, results.get(1).statusCode());
                assertEquals(0, get(app, VACATIONS).get("items").size());
                assertEquals("CONFIRMED", json.readTree(results.get(0).body()).get("affectedBookings")
                        .get(0).get("status").asString());
            }
        }
    }

    @Test
    void concurrentEditDeleteUseOneVersionAndDuplicateCreateCannotCreateTwoVacations() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock(), "Europe/Moscow")) {
            var body = dates("2026-11-02", "2026-11-02");
            var duplicates = race(() -> send(app, "POST", VACATIONS, body, "create"),
                    () -> send(app, "POST", VACATIONS, body, "create"));
            assertEquals(201, duplicates.get(0).statusCode());
            assertEquals(json.readTree(duplicates.get(0).body()), json.readTree(duplicates.get(1).body()));
            assertEquals(1, get(app, VACATIONS).get("items").size());
            var path = VACATIONS + "/" + json.readTree(duplicates.get(0).body()).get("id").asString();
            var results = race(() -> send(app, "PUT", path,
                    "{\"startDate\":\"2026-11-03\",\"endDate\":\"2026-11-03\",\"expectedVersion\":1}", "edit"),
                    () -> send(app, "DELETE", path, "{\"expectedVersion\":1}", "delete"));
            if (results.get(0).statusCode() == 200) {
                assertEquals(409, results.get(1).statusCode());
                assertEquals(2, get(app, VACATIONS).get("items").get(0).get("version").asInt());
            } else {
                assertEquals(404, results.get(0).statusCode());
                assertEquals(204, results.get(1).statusCode());
                assertEquals(0, get(app, VACATIONS).get("items").size());
            }
        }
    }

    @Test
    void previewExcludesStartedTerminalAndAlreadyInvalidBookingsUsingControllableClock() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        try (var app = start(clock, "Europe/Moscow")) {
            createType(app, "Consult", true);
            var expired = book(app, "Consult", "2026-11-02T09:00:00+03:00");
            var started = book(app, "Consult", "2026-11-02T09:30:00+03:00");
            var terminal = book(app, "Consult", "2026-11-02T10:00:00+03:00");
            book(app, "Consult", "2026-11-02T11:00:00+03:00");
            var valid = book(app, "Consult", "2026-11-02T12:00:00+03:00");
            assertEquals(200, send(app, "POST", "/api/owner/bookings/" + started.get("id").asString() + "/confirm",
                    "{\"expectedVersion\":1}", "confirm").statusCode());
            assertEquals(200, send(app, "POST", "/api/owner/bookings/" + terminal.get("id").asString() + "/reject",
                    "{\"expectedVersion\":1}", "reject").statusCode());
            var hours = "{\"expectedVersion\":1,\"days\":[{\"day\":\"MONDAY\",\"intervals\":["
                    + "{\"start\":\"09:00\",\"end\":\"11:00\"},{\"start\":\"12:00\",\"end\":\"13:00\"}]}]}";
            var warning = json.readTree(send(app, "PUT", TYPE + "/weekly-hours", hours, "hours-preview").body());
            assertEquals(200, send(app, "PUT", TYPE + "/weekly-hours",
                    consent(hours, warning), "hours-agree").statusCode());
            clock.set("2026-11-02T06:30:00Z");
            var preview = send(app, "POST", VACATIONS, dates("2026-11-02", "2026-11-02"), "preview");
            assertEquals(409, preview.statusCode(), preview.body());
            var affected = json.readTree(preview.body()).get("affectedBookings");
            assertEquals(1, affected.size());
            assertEquals(valid.get("id"), affected.get(0).get("id"));
            assertEquals("REJECTED", get(app, "/api/owner/bookings/" + expired.get("id").asString())
                    .get("status").asString());
            assertEquals("CONFIRMED", get(app, "/api/owner/bookings/" + started.get("id").asString())
                    .get("status").asString());
            assertEquals(0, get(app, VACATIONS).get("items").size());
        }
    }

    @Test
    void skippedLocalDateHasEmptyActualBoundsAndCannotCloseAnOtherwiseValidMidnightSlot() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        clock.set("2011-12-28T09:00:00Z");
        try (var app = start(clock, "Pacific/Apia")) {
            createType(app, "Consult", false);
            for (int i = 0; i < 3; i++) {
                assertEquals(200, send(app, "PUT", TYPE + "/date-overrides/2011-12-" + (29 + i),
                        "{\"expectedVersion\":" + (i + 1)
                                + ",\"intervals\":[{\"start\":\"00:00\",\"end\":\"24:00\"}]}",
                        "day-" + i).statusCode());
            }
            var before = slots(app, "Consult");
            assertTrue(before.valueStream().anyMatch(slot ->
                    slot.get("startsAt").asString().equals("2011-12-29T23:45:00-10:00")));
            assertEquals(201, send(app, "POST", VACATIONS,
                    dates("2011-12-30", "2011-12-30"), "skipped").statusCode());
            assertEquals(before, slots(app, "Consult"));
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

    private String dates(String start, String end) {
        return json.writeValueAsString(Map.of("startDate", start, "endDate", end));
    }

    private JsonNode slots(ConfigurableApplicationContext app, String type) throws Exception {
        return get(app, "/api/public/owner/meeting-types/" + type + "/slots").get("items");
    }

    private void createType(ConfigurableApplicationContext app, String id, boolean approval) throws Exception {
        var response = send(app, "POST", "/api/owner/meeting-types", json.writeValueAsString(Map.of(
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
