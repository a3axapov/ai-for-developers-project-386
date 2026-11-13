package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MeetingTypeHttpTests {
    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper json;

    private Map<String, Object> valid() {
        return new HashMap<>(Map.of("id", "Consult", "title", "Консультация", "description",
                "Достаточно длинное описание встречи", "durationMinutes", 30, "requiresApproval", false));
    }

    private HttpResponse<String> send(String method, String path, String key, String body) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
            if (key != null) {
                request.header("Idempotency-Key", key);
            }
            request.header("Content-Type", "application/json");
            return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private HttpResponse<String> create(Map<String, Object> body) throws Exception {
        return send("POST", "/api/owner/meeting-types", UUID.randomUUID().toString(), json.writeValueAsString(body));
    }

    @Test
    void creationNormalizesTextDefaultsApprovalAndReturnsConfiguredAvailability() throws Exception {
        var body = valid();
        body.put("id", "-".repeat(64));
        body.put("title", "  Разбор\u00a0  проекта 😀  ");
        body.put("description", " \n  Первая\u00a0  строка описания  \r\n\n Вторая   строка  \n ");
        body.put("durationMinutes", 1);
        body.remove("requiresApproval");
        var result = create(body);
        assertEquals(201, result.statusCode(), result.body());
        assertEquals("no-store", result.headers().firstValue("Cache-Control").orElseThrow());
        var type = json.readTree(result.body());
        assertEquals("Разбор проекта 😀", type.get("title").asString());
        assertEquals("Первая строка описания\n\nВторая строка", type.get("description").asString());
        assertEquals(false, type.get("requiresApproval").asBoolean());
        assertEquals(false, type.get("archived").asBoolean());
        assertEquals("Europe/Moscow", type.get("timeZone").asString());
        assertEquals(1, type.get("version").asInt());
        assertEquals(json.readTree("[]"), type.get("dateOverrides"));
        assertEquals(json.readTree("""
                [{"day":"MONDAY","intervals":[{"start":"09:00","end":"18:00"}]},
                 {"day":"TUESDAY","intervals":[{"start":"09:00","end":"18:00"}]},
                 {"day":"WEDNESDAY","intervals":[{"start":"09:00","end":"18:00"}]},
                 {"day":"THURSDAY","intervals":[{"start":"09:00","end":"18:00"}]},
                 {"day":"FRIDAY","intervals":[{"start":"09:00","end":"18:00"}]},
                 {"day":"SATURDAY","intervals":[]},{"day":"SUNDAY","intervals":[]}]
                """), type.get("days"));
        assertEquals(type, json.readTree(send("GET", "/api/owner/meeting-types/" + "-".repeat(64), null, null).body()));
        assertEquals(type,
                json.readTree(send("GET", "/api/owner/meeting-types", null, null).body()).get("items").get(0));
    }

    @Test
    void uniqueIdsAreCaseSensitiveAndNormalizedTitleDurationPairsAreAtomic() throws Exception {
        assertEquals(201, create(valid()).statusCode());
        var duplicateId = valid();
        duplicateId.put("title", "Другое название");
        var collision = create(duplicateId);
        assertEquals(409, collision.statusCode());
        assertEquals("DUPLICATE_MEETING_TYPE", json.readTree(collision.body()).get("code").asString());
        assertEquals("Консультация", json.readTree(send("GET", "/api/owner/meeting-types/Consult", null, null).body())
                .get("title").asString());
        var gate = new CyclicBarrier(3);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                var body = valid();
                body.put("id", "consult");
                body.put("title", "  РАЗБОР\u00a0 проекта  ");
                gate.await();
                return create(body);
            });
            var second = executor.submit(() -> {
                var body = valid();
                body.put("id", "other");
                body.put("title", "разбор   проекта");
                gate.await();
                return create(body);
            });
            gate.await();
            assertEquals(List.of(201, 409), List.of(first.get().statusCode(), second.get().statusCode())
                    .stream().sorted().toList());
        }
        var differentDuration = valid();
        differentDuration.put("id", "CONSULT");
        differentDuration.put("durationMinutes", 480);
        differentDuration.put("description", "😀".repeat(20));
        differentDuration.put("requiresApproval", true);
        assertEquals(201, create(differentDuration).statusCode());
        var list = json.readTree(send("GET", "/api/owner/meeting-types", null, null).body()).get("items");
        assertEquals(3, list.size());
    }

    @Test
    void creationReplaysAndRecoversFromSharedLedgerEvenAfterProfileChanges() throws Exception {
        var body = json.writeValueAsString(valid());
        var gate = new CyclicBarrier(3);
        HttpResponse<String> first;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> {
                gate.await();
                return send("POST", "/api/owner/meeting-types", "same", body);
            });
            var b = executor.submit(() -> {
                gate.await();
                return send("POST", "/api/owner/meeting-types", "same", body);
            });
            gate.await();
            first = a.get();
            assertEquals(201, first.statusCode());
            assertEquals(201, b.get().statusCode());
            assertEquals(json.readTree(first.body()), json.readTree(b.get().body()));
        }
        assertEquals(200, send("PUT", "/api/owner/profile", "rename",
                "{\"name\":\"Новое имя\",\"username\":\"new-owner\",\"expectedVersion\":1}").statusCode());
        assertEquals(json.readTree(first.body()),
                json.readTree(send("POST", "/api/owner/meeting-types", "same", body).body()));
        var saved = json.readTree(send("GET", "/api/owner/operations/same", null, null).body());
        assertEquals(201, saved.get("statusCode").asInt());
        assertEquals(json.readTree(first.body()), saved.get("meetingType"));
        var reused = send("PUT", "/api/owner/public-booking", "same", "{\"enabled\":true,\"expectedVersion\":2}");
        assertEquals(409, reused.statusCode());
        assertEquals("IDEMPOTENCY_KEY_REUSED", json.readTree(reused.body()).get("code").asString());
        var cross = send("POST", "/api/owner/meeting-types", "rename", body);
        assertEquals(409, cross.statusCode());
        var changed = send("POST", "/api/owner/meeting-types", "same", body.replace("Consult", "Other"));
        assertEquals(409, changed.statusCode());
        assertEquals(1, json.readTree(send("GET", "/api/owner/meeting-types", null, null).body()).get("items").size());
        assertEquals(410, send("GET", "/api/owner/operations/missing", null, null).statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "[]", "null", "", "{\"durationMinutes\":1.5}", "{\"durationMinutes\":\"30\"}",
        "{\"durationMinutes\":2147483648}", "{\"title\":4}", "{\"requiresApproval\":\"false\"}"})
    void malformedCreationIsRecoverableAndDifferentBodyCannotReuseKey(String body) throws Exception {
        var result = send("POST", "/api/owner/meeting-types", "bad", body);
        assertEquals(400, result.statusCode(), result.body());
        assertEquals("MALFORMED_REQUEST", json.readTree(result.body()).get("code").asString());
        assertEquals(json.readTree(result.body()),
                json.readTree(send("POST", "/api/owner/meeting-types", "bad", body).body()));
        var saved = json.readTree(send("GET", "/api/owner/operations/bad", null, null).body());
        assertEquals(400, saved.get("statusCode").asInt());
        assertEquals(json.readTree(result.body()), saved.get("error"));
        assertEquals(409, send("POST", "/api/owner/meeting-types", "bad", body + " ").statusCode());
        assertEquals(409,
                send("POST", "/api/owner/meeting-types", "bad", json.writeValueAsString(valid())).statusCode());
        assertEquals(0, json.readTree(send("GET", "/api/owner/meeting-types", null, null).body()).get("items").size());
    }

    @Test
    void definiteValidationAndDuplicateFailuresAreSavedAndCorrectionsNeedNewKeys() throws Exception {
        var invalid = valid();
        invalid.put("description", "short");
        var body = json.writeValueAsString(invalid);
        var failed = send("POST", "/api/owner/meeting-types", "invalid", body);
        assertEquals(422, failed.statusCode());
        assertEquals(json.readTree(failed.body()),
                json.readTree(send("GET", "/api/owner/operations/invalid", null, null).body()).get("error"));
        assertEquals(409,
                send("POST", "/api/owner/meeting-types", "invalid", json.writeValueAsString(valid())).statusCode());
        assertEquals(201, create(valid()).statusCode());
        var duplicate = send("POST", "/api/owner/meeting-types", "duplicate", json.writeValueAsString(valid()));
        assertEquals(409, duplicate.statusCode());
        assertEquals(json.readTree(duplicate.body()),
                json.readTree(send("GET", "/api/owner/operations/duplicate", null, null).body()).get("error"));
        assertEquals(json.readTree(failed.body()),
                json.readTree(send("POST", "/api/owner/meeting-types", "invalid", body).body()));
        for (String key : new String[] {null, "not.valid", "a".repeat(129)}) {
            assertEquals(400,
                    send("POST", "/api/owner/meeting-types", key, json.writeValueAsString(valid())).statusCode());
        }
        assertEquals(410, send("GET", "/api/owner/operations/not.valid", null, null).statusCode());
        assertEquals(1, json.readTree(send("GET", "/api/owner/meeting-types", null, null).body()).get("items").size());
    }

    @Test
    void publicTypesExposeShownConditionsOnlyWhenOpenAndUnknownAddressesRemain404() throws Exception {
        assertEquals(201, create(valid()).statusCode());
        var known = "/api/public/owner/meeting-types/Consult";
        var disabled = send("GET", known, null, null);
        assertEquals(200, disabled.statusCode(), disabled.body());
        assertEquals("no-store", disabled.headers().firstValue("Cache-Control").orElseThrow());
        var page = json.readTree(disabled.body());
        assertEquals("BOOKING_DISABLED", page.get("state").asString());
        assertEquals("Владелец календаря", page.get("ownerName").asString());
        assertEquals(false, page.has("meetingType"));
        for (String path : List.of("/api/public/missing/meeting-types/Consult",
                "/api/public/owner/meeting-types/consult")) {
            var result = send("GET", path, null, null);
            assertEquals(404, result.statusCode());
            assertEquals("PAGE_NOT_FOUND", json.readTree(result.body()).get("code").asString());
        }
        assertEquals(200, send("PUT", "/api/owner/public-booking", "open",
                "{\"enabled\":true,\"expectedVersion\":1}").statusCode());
        var open = json.readTree(send("GET", known, null, null).body());
        assertEquals("OPEN", open.get("state").asString());
        var type = open.get("meetingType");
        assertEquals("Consult", type.get("id").asString());
        assertEquals("Консультация", type.get("title").asString());
        assertEquals("Достаточно длинное описание встречи", type.get("description").asString());
        assertEquals(30, type.get("durationMinutes").asInt());
        assertEquals(false, type.get("requiresApproval").asBoolean());
        assertTrue(type.get("conditionsToken").asString().length() >= 32);
        assertEquals(type, json.readTree(send("GET", "/api/public/owner", null, null).body()).get("items").get(0));
        assertEquals(false, type.has("days"));
        assertEquals(200, send("PUT", "/api/owner/profile", "rename",
                "{\"name\":\"Другое имя\",\"username\":\"booking-entry\",\"expectedVersion\":2}").statusCode());
        assertEquals(404, send("GET", known, null, null).statusCode());
        var updated = json.readTree(send("GET", "/api/public/booking-entry/meeting-types/Consult", null, null).body());
        assertEquals("Другое имя", updated.get("ownerName").asString());
        assertEquals(type.get("conditionsToken"), updated.get("meetingType").get("conditionsToken"));
        assertEquals("/booking/booking-entry",
                json.readTree(send("GET", "/api/public/booking-entry/current", null, null).body())
                        .get("bookingUrl").asString());
        assertEquals(404, send("GET", "/api/owner/meeting-types/missing", null, null).statusCode());
        assertEquals(405, send("PUT", "/api/owner/meeting-types/Consult", "edit", "{}").statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\t", "\r", "\u0000", "\u0085", "\u200b", "\u034f", "\ufe0f", "\u115f", "\u2028", "\u0378"})
    void descriptionCountsVisibleCodePointsWithoutRejectingAnOtherwiseLongEnoughDescription(String suffix)
            throws Exception {
        var body = valid();
        body.put("description", "Полное описание встречи" + suffix);
        var result = create(body);
        assertEquals(201, result.statusCode(), result.body());
        assertEquals("Полное описание встречи" + suffix,
                json.readTree(result.body()).get("description").asString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"id-empty", "id-long", "id-space", "id-unicode", "id-underscore", "title-empty",
        "title-control", "title-invisible", "description-short", "description-newlines", "description-invisible",
        "description-control", "description-variation", "description-hidden-padding", "description-hidden-space-run",
        "duration-zero", "duration-large", "duration-negative",
        "missing-id", "missing-title", "missing-description", "missing-duration", "null-approval"})
    void invalidFieldsReturn422WithoutPartialCreation(String example) throws Exception {
        var body = valid();
        String field = example.split("-")[0];
        switch (example) {
            case "id-empty" -> body.put("id", "");
            case "id-long" -> body.put("id", "a".repeat(65));
            case "id-space" -> body.put("id", " A ");
            case "id-unicode" -> body.put("id", "а");
            case "id-underscore" -> body.put("id", "a_b");
            case "title-empty" -> body.put("title", " \u00a0 ");
            case "title-control" -> body.put("title", "Title\n");
            case "title-invisible" -> body.put("title", "Title\u200b");
            case "description-short" -> body.put("description", "😀".repeat(19));
            case "description-newlines" -> body.put("description", "a".repeat(18) + " b\n\n\n".replace("b", ""));
            case "description-invisible" -> body.put("description", "a".repeat(19) + "\u200b".repeat(10));
            case "description-control" -> body.put("description", "a".repeat(19) + "\t");
            case "description-variation" -> body.put("description", "a".repeat(19) + "\ufe0f".repeat(10));
            case "description-hidden-padding" -> body.put("description", "a".repeat(19) + " \u200b");
            case "description-hidden-space-run" -> body.put("description", "a".repeat(8) + " \u200b \u200b "
                    + "b".repeat(9));
            case "duration-zero" -> body.put("durationMinutes", 0);
            case "duration-large" -> body.put("durationMinutes", 481);
            case "duration-negative" -> body.put("durationMinutes", -1);
            case "null-approval" -> {
                field = "requiresApproval";
                body.put(field, null);
            }
            default -> {
                field = example.substring(8);
                body.remove(field.equals("duration") ? "durationMinutes" : field);
            }
        }
        if (field.equals("duration")) {
            field = "durationMinutes";
        }
        var result = example.equals("null-approval") ? send("POST", "/api/owner/meeting-types", "null",
                json.writeValueAsString(valid()).replace("\"requiresApproval\":false", "\"requiresApproval\":null"))
                : create(body);
        assertEquals(422, result.statusCode(), result.body());
        var error = json.readTree(result.body());
        assertEquals("VALIDATION_FAILED", error.get("code").asString());
        assertEquals(field, error.get("fieldErrors").get(0).get("field").asString());
        assertEquals("INVALID_VALUE", error.get("fieldErrors").get(0).get("code").asString());
        assertTrue(send("GET", "/api/owner/meeting-types", null, null).body().contains("[]"));
        assertEquals(1, json.readTree(send("GET", "/api/owner/profile", null, null).body()).get("version").asInt());
    }

    @Test
    void ownerCanCreateMeetingType() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/owner/meeting-types"))
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", "create-first")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {"id":"First","title":"Встреча","description":"Достаточно длинное описание встречи",
                             "durationMinutes":30,"requiresApproval":false}
                            """))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, response.statusCode());
        }
    }

    @Test
    void createdTypeAppearsInOwnerList() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var base = "http://127.0.0.1:" + port + "/api/owner/meeting-types";
            var create = HttpRequest.newBuilder(URI.create(base))
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", "create-list-test")
                    .POST(HttpRequest.BodyPublishers.ofString("""
                            {"id":"ListTest","title":"Встреча для списка",
                             "description":"Достаточно длинное описание встречи",
                             "durationMinutes":30,"requiresApproval":false}
                            """))
                    .build();
            assertEquals(201, client.send(create, HttpResponse.BodyHandlers.ofString()).statusCode());
            var list = client.send(HttpRequest.newBuilder(URI.create(base)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, list.statusCode());
            org.junit.jupiter.api.Assertions.assertTrue(list.body().contains("ListTest"));
        }
    }
}
