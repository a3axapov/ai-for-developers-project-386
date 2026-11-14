package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.HashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MeetingTypeEditHttpTests {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void editPreservesIdentityAndAvailabilityAndReplaysOriginalOutcome() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var original = create(app, "Consult", "Консультация");
            var body = editBody("  Новая\u00a0 консультация  ", 1);
            var response = send(app, "PUT", "/api/owner/meeting-types/Consult", body, "edit");
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            var edited = json.readTree(response.body());
            assertEquals("Consult", edited.get("id").asString());
            assertEquals("Новая консультация", edited.get("title").asString());
            assertEquals(2, edited.get("version").asInt());
            assertEquals(original.get("days"), edited.get("days"));
            assertEquals(original.get("dateOverrides"), edited.get("dateOverrides"));
            assertEquals(original.get("timeZone"), edited.get("timeZone"));
            assertEquals(edited, get(app, "/api/owner/meeting-types/Consult"));
            assertEquals(edited,
                    json.readTree(send(app, "PUT", "/api/owner/meeting-types/Consult", body, "edit").body()));
            var operation = get(app, "/api/owner/operations/edit");
            assertEquals(200, operation.get("statusCode").asInt());
            assertEquals(edited, operation.get("meetingType"));
            assertEquals(200, send(app, "POST", "/api/owner/meeting-types/Consult/archive",
                    "{\"expectedVersion\":2}", "later-archive").statusCode());
            assertEquals(edited,
                    json.readTree(send(app, "PUT", "/api/owner/meeting-types/Consult", body, "edit").body()));
            assertEquals(edited, get(app, "/api/owner/operations/edit").get("meetingType"));
        }
    }

    private String editBody(String title, int version) {
        return json.writeValueAsString(Map.of("title", title, "description",
                "Достаточно длинное описание встречи", "durationMinutes", 30,
                "requiresApproval", false, "expectedVersion", version));
    }

    @Test
    void archiveIsIrreversibleReservesIdButReleasesActivePairAndHasPublicStatePriority() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            create(app, "Consult", "Консультация");
            var path = "/api/owner/meeting-types/Consult";
            var archived = send(app, "POST", path + "/archive", "{\"expectedVersion\":1}", "archive");
            assertEquals(200, archived.statusCode(), archived.body());
            var type = json.readTree(archived.body());
            assertTrue(type.get("archived").asBoolean());
            assertEquals(2, type.get("version").asInt());
            assertEquals(type, get(app, path));
            assertEquals(type, get(app, "/api/owner/operations/archive").get("meetingType"));
            assertEquals(type, json.readTree(send(app, "POST", path + "/archive",
                    "{\"expectedVersion\":1}", "archive").body()));
            var edit = send(app, "PUT", path, editBody("Новый тип", 2), "archived-edit");
            assertEquals(409, edit.statusCode());
            assertEquals("ACTION_NOT_ALLOWED", json.readTree(edit.body()).get("code").asString());
            assertEquals(type, json.readTree(edit.body()).get("currentMeetingType"));
            assertEquals(409, send(app, "POST", path + "/archive",
                    "{\"expectedVersion\":2}", "again").statusCode());
            assertEquals(type, get(app, path));
            assertEquals(0, get(app, "/api/public/owner").get("items").size());
            var publicPath = "/api/public/owner/meeting-types/Consult";
            var page = get(app, publicPath);
            assertEquals("TYPE_ARCHIVED", page.get("state").asString());
            assertFalse(page.has("meetingType"));
            assertEquals("TYPE_ARCHIVED",
                    json.readTree(send(app, "GET", publicPath + "/slots", null, null).body()).get("code").asString());
            assertEquals(200, send(app, "PUT", "/api/owner/public-booking",
                    "{\"enabled\":false,\"expectedVersion\":1}", "disable").statusCode());
            assertEquals("BOOKING_DISABLED", get(app, publicPath).get("state").asString());
            assertEquals(404, send(app, "GET", publicPath + "missing", null, null).statusCode());
            assertEquals("BOOKING_DISABLED",
                    json.readTree(send(app, "GET", publicPath + "/slots", null, null).body()).get("code").asString());
            create(app, "Other", "Консультация");
            var duplicateId = send(app, "POST", "/api/owner/meeting-types", json.writeValueAsString(Map.of(
                    "id", "Consult", "title", "Другое название", "description",
                    "Достаточно длинное описание встречи", "durationMinutes", 45)), "reserved");
            assertEquals(409, duplicateId.statusCode());
            assertEquals("DUPLICATE_MEETING_TYPE", json.readTree(duplicateId.body()).get("code").asString());
        }
    }

    private JsonNode create(ConfigurableApplicationContext app, String id, String title) throws Exception {
        var response = send(app, "POST", "/api/owner/meeting-types", json.writeValueAsString(Map.of(
                "id", id, "title", title, "description", "Достаточно длинное описание встречи",
                "durationMinutes", 30)), UUID.randomUUID().toString());
        assertEquals(201, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    @Test
    void changedConditionsRejectPreparedAttemptWithCurrentConditionsButPreserveExistingSnapshot() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            create(app, "Consult", "Консультация");
            var publicPath = "/api/public/owner/meeting-types/Consult";
            var oldConditions = get(app, publicPath).get("meetingType");
            var first = prepare(app, oldConditions, "2026-11-02T09:00:00+03:00");
            var bookedResponse = submit(app, first);
            assertEquals(201, bookedResponse.statusCode(), bookedResponse.body());
            var booked = json.readTree(bookedResponse.body());
            var prepared = prepare(app, oldConditions, "2026-11-02T10:00:00+03:00");
            var changed = send(app, "PUT", "/api/owner/meeting-types/Consult",
                    editBody("Новые условия", 1).replace("\"durationMinutes\":30", "\"durationMinutes\":45")
                            .replace("\"requiresApproval\":false", "\"requiresApproval\":true")
                            .replace("Достаточно длинное описание встречи", "Совершенно другое описание встречи"),
                    "change");
            assertEquals(200, changed.statusCode(), changed.body());
            var current = get(app, publicPath).get("meetingType");
            var failed = submit(app, prepared);
            assertEquals(409, failed.statusCode());
            var error = json.readTree(failed.body());
            assertEquals("CONDITIONS_CHANGED", error.get("code").asString());
            assertEquals(current, error.get("currentConditions"));
            assertEquals("no-store", failed.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(error, json.readTree(submit(app, prepared).body()));
            assertEquals(booked.get("booking"), get(app, "/api" + booked.get("resultUrl").asString()));
            assertEquals(1, get(app, "/api/owner/bookings?view=upcoming").get("items").size());
            var newer = json.readTree(submit(app, prepare(app, current, "2026-11-02T10:00:00+03:00")).body());
            assertEquals("Новые условия", newer.get("booking").get("title").asString());
            assertEquals(45, newer.get("booking").get("durationMinutes").asInt());
            assertEquals("PENDING", newer.get("booking").get("status").asString());
            assertEquals("Консультация", booked.get("booking").get("title").asString());
            assertEquals(false, booked.get("booking").get("requiresApproval").asBoolean());
        }
    }

    private JsonNode prepare(ConfigurableApplicationContext app, JsonNode conditions, String startsAt)
            throws Exception {
        var result = send(app, "POST", "/api/public/owner/booking-attempts", json.writeValueAsString(Map.of(
                "meetingTypeId", conditions.get("id").asString(),
                "conditionsToken", conditions.get("conditionsToken").asString(), "startsAt", startsAt,
                "guestName", "Гость", "guestEmail", "guest@example.com", "comment", "Контакты сохранены")), null);
        assertEquals(201, result.statusCode(), result.body());
        return json.readTree(result.body());
    }

    @Test
    void unchangedNormalizedSaveAndOwnerRenameDoNotStaleShownConditions() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            create(app, "Consult", "Консультация");
            var conditions = get(app, "/api/public/owner/meeting-types/Consult").get("meetingType");
            var prepared = prepare(app, conditions, "2026-11-02T09:00:00+03:00");
            assertEquals(200, send(app, "PUT", "/api/owner/meeting-types/Consult",
                    editBody("  Консультация\u00a0 ", 1)
                            .replace("Достаточно длинное описание встречи",
                                    "  Достаточно   длинное описание встречи  \\n "), "same").statusCode());
            assertEquals(200, send(app, "PUT", "/api/owner/profile",
                    "{\"name\":\"Другое имя\",\"username\":\"owner\",\"expectedVersion\":1}", "name").statusCode());
            var current = get(app, "/api/public/owner/meeting-types/Consult").get("meetingType");
            assertEquals(conditions.get("conditionsToken"), current.get("conditionsToken"));
            assertEquals(201, submit(app, prepared).statusCode());
            assertEquals(201, send(app, "POST", "/api/public/owner/booking-attempts",
                    json.writeValueAsString(Map.of("meetingTypeId", "Consult",
                            "conditionsToken", conditions.get("conditionsToken").asString(),
                            "startsAt", "2026-11-02T10:00:00+03:00",
                            "guestName", "Гость", "guestEmail", "guest@example.com")), null).statusCode());
        }
    }

    @Test
    void archivedBookingsRemainManageableAndLateSuccessfulAttemptRecoveryPrecedesNewBookingChecks() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        try (var app = start(clock)) {
            create(app, "Consult", "Консультация");
            assertEquals(200, send(app, "PUT", "/api/owner/meeting-types/Consult",
                    editBody("Консультация", 1).replace("\"requiresApproval\":false", "\"requiresApproval\":true"),
                    "manual").statusCode());
            var conditions = get(app, "/api/public/owner/meeting-types/Consult").get("meetingType");
            var prepared = prepare(app, conditions, "2026-11-02T09:00:00+03:00");
            var saved = json.readTree(submit(app, prepared).body());
            var waiting = prepare(app, conditions, "2026-11-02T10:00:00+03:00");
            var booking = saved.get("booking");
            assertEquals("PENDING", booking.get("status").asString());
            assertEquals(200, send(app, "POST", "/api/owner/meeting-types/Consult/archive",
                    "{\"expectedVersion\":2}", "archive").statusCode());
            assertEquals(booking, get(app, "/api/owner/bookings?view=upcoming").get("items").get(0));
            var calendar = get(app, "/api/owner/calendar?from=2026-11-02T00:00:00Z&to=2026-11-03T00:00:00Z");
            assertEquals(booking, calendar.get("items").get(0));
            assertFalse(calendar.toString().contains("/bookings/"));
            var refused = submit(app, waiting);
            assertEquals(409, refused.statusCode());
            assertEquals("TYPE_ARCHIVED", json.readTree(refused.body()).get("code").asString());
            var confirm = send(app, "POST", "/api/owner/bookings/" + booking.get("id").asString() + "/confirm",
                    "{\"expectedVersion\":1}", "confirm");
            assertEquals(200, confirm.statusCode(), confirm.body());
            var confirmed = json.readTree(confirm.body());
            assertEquals("CONFIRMED", confirmed.get("status").asString());
            assertEquals(booking.get("description"), confirmed.get("description"));
            assertEquals(true, confirmed.get("requiresApproval").asBoolean());
            assertEquals(200, send(app, "PUT", "/api/owner/profile",
                    "{\"name\":\"Имя\",\"username\":\"changed\",\"expectedVersion\":1}", "rename").statusCode());
            assertEquals(200, send(app, "PUT", "/api/owner/public-booking",
                    "{\"enabled\":false,\"expectedVersion\":2}", "off").statusCode());
            clock.set("2027-01-01T00:00:00Z");
            var replay = submit(app, prepared);
            assertEquals(200, replay.statusCode(), replay.body());
            var recovered = json.readTree(replay.body());
            assertEquals(saved.get("resultUrl"), recovered.get("resultUrl"));
            assertEquals(confirmed.get("id"), recovered.get("booking").get("id"));
            assertEquals("CONFIRMED", recovered.get("booking").get("status").asString());
            assertEquals(recovered.get("booking"), get(app, "/api" + saved.get("resultUrl").asString()));
            assertEquals(1, get(app, "/api/owner/bookings?view=history").get("items").size());
        }
    }

    @Test
    void editValidationFailuresAreRetainedWithoutPartialChangesAndIdCannotBeChanged() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var original = create(app, "Consult", "Консультация");
            for (var entry : List.of(Map.entry("title", ""), Map.entry("title", "bad\tname"),
                    Map.entry("description", "a".repeat(19) + "\n\n\u200b"),
                    Map.entry("durationMinutes", 0), Map.entry("durationMinutes", 481),
                    Map.entry("requiresApproval", "null"), Map.entry("expectedVersion", 0))) {
                var body = new HashMap<String, Object>(Map.of("title", "Новый тип", "description",
                        "Достаточно длинное описание встречи", "durationMinutes", 30,
                        "requiresApproval", false, "expectedVersion", 1));
                body.put(entry.getKey(), entry.getValue().equals("null") ? null : entry.getValue());
                var text = json.writeValueAsString(body);
                var key = UUID.randomUUID().toString();
                var result = send(app, "PUT", "/api/owner/meeting-types/Consult", text, key);
                assertEquals(422, result.statusCode(), result.body());
                var error = json.readTree(result.body());
                assertEquals("VALIDATION_FAILED", error.get("code").asString());
                assertEquals(entry.getKey(), error.get("fieldErrors").get(0).get("field").asString());
                assertEquals(error, get(app, "/api/owner/operations/" + key).get("error"));
                assertEquals(error,
                        json.readTree(send(app, "PUT", "/api/owner/meeting-types/Consult", text, key).body()));
                assertEquals(409, send(app, "PUT", "/api/owner/meeting-types/Consult",
                        editBody("Исправленный тип", 1), key).statusCode());
                assertEquals(original, get(app, "/api/owner/meeting-types/Consult"));
            }
            for (int duration : List.of(1, 480)) {
                var result = send(app, "PUT", "/api/owner/meeting-types/Consult",
                        editBody("Консультация", duration == 1 ? 1 : 2)
                                .replace("\"durationMinutes\":30", "\"durationMinutes\":" + duration)
                                .replace("Достаточно длинное описание встречи", "😀".repeat(20))
                                .replace("{", "{\"id\":\"Changed\","),
                        UUID.randomUUID().toString());
                assertEquals(200, result.statusCode(), result.body());
                assertEquals("Consult", json.readTree(result.body()).get("id").asString());
            }
            assertEquals(404, send(app, "GET", "/api/owner/meeting-types/Changed", null, null).statusCode());
        }
    }

    @Test
    void malformedEditAndArchiveCommandsUseSharedLedgerWithPathAndImmutableFailure() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var original = create(app, "Consult", "Консультация");
            for (var method : List.of("PUT", "POST")) {
                var path = "/api/owner/meeting-types/Consult" + (method.equals("POST") ? "/archive" : "");
                for (var body : List.of("{", "[]", "null", "", "{\"expectedVersion\":1.5}",
                        "{\"expectedVersion\":\"1\"}")) {
                    var key = UUID.randomUUID().toString();
                    var failed = send(app, method, path, body, key);
                    assertEquals(400, failed.statusCode(), failed.body());
                    assertEquals("no-store", failed.headers().firstValue("Cache-Control").orElseThrow());
                    assertEquals("MALFORMED_REQUEST", json.readTree(failed.body()).get("code").asString());
                    assertEquals(json.readTree(failed.body()), get(app, "/api/owner/operations/" + key).get("error"));
                    assertEquals(json.readTree(failed.body()),
                            json.readTree(send(app, method, path, body, key).body()));
                    assertEquals(409, send(app, method, path, body + " ", key).statusCode());
                    assertEquals(409, send(app, "PUT", "/api/owner/profile",
                            "{\"name\":\"Имя\",\"username\":\"owner\",\"expectedVersion\":1}", key).statusCode());
                    assertEquals(original, get(app, "/api/owner/meeting-types/Consult"));
                }
                for (var key : java.util.Arrays.asList(null, "bad.key", "x".repeat(129))) {
                    assertEquals(400, send(app, method, path, "{\"expectedVersion\":1}", key).statusCode());
                }
                var invalid = send(app, method, path, "{\"expectedVersion\":0}", UUID.randomUUID().toString());
                assertEquals(422, invalid.statusCode());
                var missing = send(app, method, path.replace("Consult", "missing"),
                        method.equals("POST") ? "{\"expectedVersion\":1}" : editBody("Имя", 1),
                        UUID.randomUUID().toString());
                assertEquals(404, missing.statusCode());
            }
        }
    }

    @Test
    void concurrentEditsEnforceActivePairUniquenessAndEditArchiveVersionConflictAtomically() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            create(app, "One", "Первый");
            create(app, "Two", "Второй");
            var gate = new CyclicBarrier(3);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var first = executor.submit(() -> {
                    gate.await();
                    return send(app, "PUT", "/api/owner/meeting-types/One", editBody("  ОБЩИЙ\u00a0 тип ", 1), "one");
                });
                var second = executor.submit(() -> {
                    gate.await();
                    return send(app, "PUT", "/api/owner/meeting-types/Two", editBody("общий   тип", 1), "two");
                });
                gate.await();
                var results = List.of(first.get(), second.get());
                assertEquals(List.of(200, 409), results.stream().map(HttpResponse::statusCode).sorted().toList());
                var loser = results.stream().filter(result -> result.statusCode() == 409).findFirst().orElseThrow();
                assertEquals("DUPLICATE_MEETING_TYPE", json.readTree(loser.body()).get("code").asString());
            }
            var before = get(app, "/api/owner/meeting-types/One");
            var racing = new CyclicBarrier(3);
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var first = executor.submit(() -> {
                    racing.await();
                    return send(app, "PUT", "/api/owner/meeting-types/One",
                            editBody("Последний тип", before.get("version").asInt()), "edit-race");
                });
                var second = executor.submit(() -> {
                    racing.await();
                    return send(app, "POST", "/api/owner/meeting-types/One/archive",
                            "{\"expectedVersion\":" + before.get("version").asInt() + "}", "archive-race");
                });
                racing.await();
                var results = List.of(first.get(), second.get());
                assertEquals(List.of(200, 409), results.stream().map(HttpResponse::statusCode).sorted().toList());
                var current = get(app, "/api/owner/meeting-types/One");
                assertEquals(before.get("version").asInt() + 1, current.get("version").asInt());
                var loser = json.readTree(results.stream().filter(result -> result.statusCode() == 409)
                        .findFirst().orElseThrow().body());
                assertEquals("RESOURCE_CHANGED", loser.get("code").asString());
                assertEquals(current, loser.get("currentMeetingType"));
                assertEquals(current, json.readTree(results.stream().filter(result -> result.statusCode() == 200)
                        .findFirst().orElseThrow().body()));
            }
        }
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

    private ConfigurableApplicationContext start(BookingSubmissionHttpTests.MutableClock clock) {
        return new SpringApplicationBuilder(CalendarApplication.class)
                .initializers(context -> context.getBeanFactory().registerSingleton("testClock", clock))
                .run("--server.port=0", "--calendar.initial.booking-enabled=true");
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
