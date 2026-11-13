package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class BookingAttemptHttpTests {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void addressAndPublicStateTakePriorityOverOtherPreparationErrors() throws Exception {
        try (var app = start()) {
            seed(app);
            var command = command(app);
            command.put("guestName", "");
            command.put("conditionsToken", "stale");
            assertError(app, "/api/public/unknown/booking-attempts", command, 404, "PAGE_NOT_FOUND");
            assertEquals(200, send(app, "PUT", "/api/owner/public-booking",
                    "{\"enabled\":false,\"expectedVersion\":1}").statusCode());
            assertError(app, "/api/public/owner/booking-attempts", command, 409, "BOOKING_DISABLED");
            command.put("meetingTypeId", "unknown");
            assertError(app, "/api/public/owner/booking-attempts", command, 404, "PAGE_NOT_FOUND");
        }
    }

    @Test
    void acceptsNormalizedUnicodeBoundariesAndOptionalCommentWithoutOfferingMutationOrUnauthenticatedRead()
            throws Exception {
        try (var app = start()) {
            seed(app);
            var command = command(app);
            // 64 + 1 + 63 + 1 + 63 + 1 + 61 = 254 code points.
            var email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
            command.put("guestEmail", "\u00a0" + email + "\u202f");
            for (var name : java.util.List.of("李", "  " + "🦊".repeat(100) + "  ", "  Анна\u00a0\u202f 李  ")) {
                command.put("guestName", name);
                command.put("comment", "🦊".repeat(1998) + "\r\n");
                var response = send(app, "POST", "/api/public/owner/booking-attempts",
                        json.writeValueAsString(command));
                assertEquals(201, response.statusCode(), response.body());
                var id = json.readTree(response.body()).get("attemptId").asString();
                for (var method : java.util.List.of("GET", "PUT", "PATCH", "DELETE")) {
                    var denied = send(app, method, "/api/booking-attempts/" + id,
                            method.equals("GET") ? null : json.writeValueAsString(command));
                    assertTrue(denied.statusCode() == 404 || denied.statusCode() == 405, denied.body());
                    assertFalse(denied.body().contains("attemptSecret"));
                    assertFalse(denied.body().contains(email));
                }
            }
            command.remove("comment");
            assertEquals(201, send(app, "POST", "/api/public/owner/booking-attempts",
                    json.writeValueAsString(command)).statusCode());
            command.put("comment", "");
            assertEquals(201, send(app, "POST", "/api/public/owner/booking-attempts",
                    json.writeValueAsString(command)).statusCode());
            command.put("guestEmail", " Guest+Tag@Example.COM ");
            assertEquals(201, send(app, "POST", "/api/public/owner/booking-attempts",
                    json.writeValueAsString(command)).statusCode());
            command.put("guestName", "🦊".repeat(101));
            assertError(app, "/api/public/owner/booking-attempts", command, 422, "VALIDATION_FAILED");
            command.put("guestName", "李");
            command.put("comment", "🦊".repeat(2001));
            assertError(app, "/api/public/owner/booking-attempts", command, 422, "VALIDATION_FAILED");
        }
    }

    @Test
    void preparationRespectsAddressAndPublicStateAndShownConditions() throws Exception {
        try (var app = start()) {
            seed(app);
            var command = command(app);
            var before = get(app, "/api/public/owner/meeting-types/Consult/slots");
            var stale = new java.util.HashMap<>(command);
            stale.put("conditionsToken", "outdated");
            assertError(app, "/api/public/owner/booking-attempts", stale, 409, "CONDITIONS_CHANGED");
            var missing = new java.util.HashMap<>(command);
            missing.put("meetingTypeId", "unknown");
            assertError(app, "/api/public/owner/booking-attempts", missing, 404, "PAGE_NOT_FOUND");
            assertError(app, "/api/public/unknown/booking-attempts", command, 404, "PAGE_NOT_FOUND");
            assertEquals(before, get(app, "/api/public/owner/meeting-types/Consult/slots"));
            assertEquals(200, send(app, "PUT", "/api/owner/public-booking",
                    "{\"enabled\":false,\"expectedVersion\":1}").statusCode());
            assertError(app, "/api/public/owner/booking-attempts", command, 409, "BOOKING_DISABLED");
            assertError(app, "/api/public/owner/booking-attempts", missing, 404, "PAGE_NOT_FOUND");
            assertError(app, "/api/public/unknown/booking-attempts", command, 404, "PAGE_NOT_FOUND");
        }
    }

    private void assertError(ConfigurableApplicationContext app, String path, Map<String, Object> command,
            int status, String code) throws Exception {
        var response = send(app, "POST", path, json.writeValueAsString(command));
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(code, json.readTree(response.body()).get("code").asString());
        assertFalse(response.body().contains("attemptSecret"));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
    }

    @Test
    void requiresCompleteSelectionAndRejectsMalformedTransportWithoutLeakingInput() throws Exception {
        try (var app = start()) {
            seed(app);
            for (var field : java.util.List.of("meetingTypeId", "conditionsToken", "startsAt")) {
                var command = command(app);
                command.remove(field);
                var response = send(app, "POST", "/api/public/owner/booking-attempts",
                        json.writeValueAsString(command));
                assertEquals(422, response.statusCode(), response.body());
                assertEquals(field, json.readTree(response.body()).get("fieldErrors").get(0).get("field").asString());
            }
            for (var raw : java.util.List.of("{", "[]", "null", "{\"startsAt\":\"2026-11-02T09:00:00\"}",
                    "{\"startsAt\":\"tomorrow\"}", "{\"guestName\":42}", "{\"guestEmail\":false}",
                    "{\"comment\":{}}")) {
                var response = send(app, "POST", "/api/public/owner/booking-attempts", raw);
                assertEquals(400, response.statusCode(), response.body());
                assertEquals("MALFORMED_REQUEST", json.readTree(response.body()).get("code").asString());
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            }
        }
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> invalidContacts() {
        var cases = new java.util.ArrayList<org.junit.jupiter.params.provider.Arguments>();
        for (var field : java.util.List.of("guestName", "guestEmail", "comment")) {
            for (var forbidden : java.util.List.of("\t", "\u0000", "\u007f", "\u0085", "\u200b", "\u202e",
                    "\u034f", "\u115f", "\u17b4", "\u180b", "\u3164", "\ufe0f", "\uffa0", "\u2028", "\u2029")) {
                cases.add(org.junit.jupiter.params.provider.Arguments.of(field,
                        field.equals("guestEmail") ? "guest" + forbidden + "@example.com" : "Гость" + forbidden));
            }
        }
        for (var field : java.util.List.of("guestName", "guestEmail")) {
            for (var line : java.util.List.of("\n", "\r", "\r\n")) {
                cases.add(org.junit.jupiter.params.provider.Arguments.of(field,
                        field.equals("guestEmail") ? "guest@example.com" + line : "Гость" + line));
            }
            cases.add(org.junit.jupiter.params.provider.Arguments.of(field, "  "));
            cases.add(org.junit.jupiter.params.provider.Arguments.of(field, (Object) null));
        }
        cases.add(org.junit.jupiter.params.provider.Arguments.of("guestName", "я".repeat(101)));
        cases.add(org.junit.jupiter.params.provider.Arguments.of("comment", "я".repeat(2001)));
        for (var email : java.util.List.of("no-at", "a@@example.com", "a b@example.com", "a@example..com",
                ".a@example.com", "a.@example.com", "a,b@example.com", "a@example.com,b@example.com",
                "a@-example.com", "a@example-.com", "a@", "@example.com", "a".repeat(243) + "@example.com")) {
            cases.add(org.junit.jupiter.params.provider.Arguments.of("guestEmail", email));
        }
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("invalidContacts")
    void rejectsInvalidContactsWithoutReservation(String field, String value) throws Exception {
        try (var app = start()) {
            seed(app);
            var before = get(app, "/api/public/owner/meeting-types/Consult/slots");
            var command = command(app);
            if (value == null) {
                command.remove(field);
            } else {
                command.put(field, value);
            }
            var response = send(app, "POST", "/api/public/owner/booking-attempts", json.writeValueAsString(command));
            assertEquals(422, response.statusCode(), response.body());
            var error = json.readTree(response.body());
            assertEquals("VALIDATION_FAILED", error.get("code").asString());
            assertEquals(field, error.get("fieldErrors").get(0).get("field").asString());
            assertEquals("INVALID_VALUE", error.get("fieldErrors").get(0).get("code").asString());
            assertFalse(error.get("fieldErrors").get(0).get("message").asString().isBlank());
            assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            assertFalse(response.body().contains("attemptSecret"));
            assertEquals(before, get(app, "/api/public/owner/meeting-types/Consult/slots"));
        }
    }

    @Test
    void preparationReturnsIndependentCredentialsWithoutReservingTimeOrExposingContacts() throws Exception {
        try (var app = start()) {
            seed(app);
            var before = get(app, "/api/public/owner/meeting-types/Consult/slots");
            var command = command(app);
            var first = send(app, "POST", "/api/public/owner/booking-attempts", json.writeValueAsString(command));
            assertEquals(201, first.statusCode(), first.body());
            assertEquals("no-store", first.headers().firstValue("Cache-Control").orElseThrow());
            var attempt = json.readTree(first.body());
            assertEquals(3, attempt.size());
            assertEquals("PREPARED", attempt.get("state").asString());
            assertTrue(attempt.get("attemptId").asString().matches("[A-Za-z0-9_-]{32,}"));
            assertTrue(attempt.get("attemptSecret").asString().matches("[0-9a-f]{64,}"));
            assertNotEquals(attempt.get("attemptId"), attempt.get("attemptSecret"));
            var second = json.readTree(send(app, "POST", "/api/public/owner/booking-attempts",
                    json.writeValueAsString(command)).body());
            assertNotEquals(attempt.get("attemptId"), second.get("attemptId"));
            assertNotEquals(attempt.get("attemptSecret"), second.get("attemptSecret"));
            assertEquals(before, get(app, "/api/public/owner/meeting-types/Consult/slots"));
            var publicPage = get(app, "/api/public/owner").toString();
            assertFalse(publicPage.contains("guest@example.com"));
            assertFalse(publicPage.contains(attempt.get("attemptSecret").asString()));
        }
    }

    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(CalendarApplication.class)
                .initializers(context -> context.getBeanFactory().registerSingleton("testClock",
                        Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC)))
                .run("--server.port=0", "--calendar.initial.booking-enabled=true");
    }

    private void seed(ConfigurableApplicationContext app) throws Exception {
        assertEquals(201, send(app, "POST", "/api/owner/meeting-types", """
                {"id":"Consult","title":"Консультация","description":"Подробное описание консультации",
                 "durationMinutes":30}
                """).statusCode());
    }

    private Map<String, Object> command(ConfigurableApplicationContext app) throws Exception {
        var slots = get(app, "/api/public/owner/meeting-types/Consult/slots");
        return new java.util.HashMap<>(Map.of("meetingTypeId", "Consult",
                "conditionsToken", slots.get("conditionsToken").asString(),
                "startsAt", "2026-11-02T09:00:00+03:00", "guestName", "Гость",
                "guestEmail", "guest@example.com", "comment", "Вопрос\nВторая строка"));
    }

    private JsonNode get(ConfigurableApplicationContext app, String path) throws Exception {
        var response = send(app, "GET", path, null);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private HttpResponse<String> send(ConfigurableApplicationContext app, String method, String path, String body)
            throws Exception {
        int port = ((WebServerApplicationContext) app).getWebServer().getPort();
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json");
        if (path.startsWith("/api/owner/") && !method.equals("GET")) {
            builder.header("Idempotency-Key", "fixture-" + path.hashCode());
        }
        var request = builder
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body)).build();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
