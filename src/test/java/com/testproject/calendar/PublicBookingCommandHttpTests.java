package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PublicBookingCommandHttpTests {

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper json;

    @Test
    void ownerCanEnableWithoutTypesAndDisableAgain() throws Exception {
        var enabled = put("enable", "{\"enabled\":true,\"expectedVersion\":1}");
        assertEquals(200, enabled.statusCode());
        assertEquals("no-store", enabled.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(json.readTree("""
                {"name":"Владелец календаря","username":"owner","timeZone":"Europe/Moscow",
                 "bookingEnabled":true,"version":2,"bookingUrl":"/booking/owner"}
                """), json.readTree(enabled.body()));
        assertEquals(json.readTree(enabled.body()), json.readTree(get("/api/owner/profile").body()));
        assertEquals(json.readTree("""
                {"ownerName":"Владелец календаря","ownerTimeZone":"Europe/Moscow","state":"OPEN","items":[]}
                """), json.readTree(get("/api/public/owner").body()));

        var disabled = put("disable", "{\"enabled\":false,\"expectedVersion\":2}");
        assertEquals(200, disabled.statusCode());
        assertFalse(json.readTree(disabled.body()).get("bookingEnabled").asBoolean());
        assertEquals(3, json.readTree(disabled.body()).get("version").asInt());
        var page = json.readTree(get("/api/public/owner").body());
        assertEquals("BOOKING_DISABLED", page.get("state").asString());
        assertFalse(page.has("items"));
    }

    @Test
    void staleVersionReturnsCurrentProfileWithoutPartialChanges() throws Exception {
        var enabled = put("first", "{\"enabled\":true,\"expectedVersion\":1}");
        var stale = put("stale", "{\"enabled\":false,\"expectedVersion\":1}");
        assertEquals(409, stale.statusCode());
        var error = json.readTree(stale.body());
        assertEquals("RESOURCE_CHANGED", error.get("code").asString());
        assertEquals(json.readTree(enabled.body()), error.get("currentProfile"));
        assertEquals(json.readTree(enabled.body()), json.readTree(get("/api/owner/profile").body()));
        assertEquals("OPEN", json.readTree(get("/api/public/owner").body()).get("state").asString());
    }

    @Test
    void sameKeyReplaysSavedSuccessBeforeCheckingVersionAndDoesNotReapply() throws Exception {
        var first = put("saved", "{\"enabled\":true,\"expectedVersion\":1}");
        var current = put("later", "{\"enabled\":false,\"expectedVersion\":2}");
        var replay = put("saved", "{\"expectedVersion\":1, \"enabled\":true}");
        assertEquals(200, replay.statusCode());
        assertEquals(json.readTree(first.body()), json.readTree(replay.body()));
        assertEquals(json.readTree(current.body()), json.readTree(get("/api/owner/profile").body()));
    }

    @Test
    void differentRequestUnderSavedKeyIsRejectedWithoutReplacingTheOutcome() throws Exception {
        var first = put("saved", "{\"enabled\":true,\"expectedVersion\":1}");
        var reused = put("saved", "{\"enabled\":false,\"expectedVersion\":2}");
        assertEquals(409, reused.statusCode());
        assertEquals("IDEMPOTENCY_KEY_REUSED", json.readTree(reused.body()).get("code").asString());
        assertEquals(json.readTree(first.body()), json.readTree(get("/api/owner/profile").body()));
        var replay = put("saved", "{\"enabled\":true,\"expectedVersion\":1}");
        assertEquals(200, replay.statusCode());
        assertEquals(json.readTree(first.body()), json.readTree(replay.body()));
    }

    @Test
    void definiteVersionFailureIsSavedEvenWhenItsVersionLaterBecomesCurrent() throws Exception {
        var failed = put("failed", "{\"enabled\":false,\"expectedVersion\":2}");
        assertEquals(409, failed.statusCode());
        var current = put("enable", "{\"enabled\":true,\"expectedVersion\":1}");
        var replay = put("failed", "{\"enabled\":false,\"expectedVersion\":2}");
        assertEquals(409, replay.statusCode());
        assertEquals(json.readTree(failed.body()), json.readTree(replay.body()));
        var reused = put("failed", "{\"enabled\":true,\"expectedVersion\":2}");
        assertEquals(409, reused.statusCode());
        assertEquals("IDEMPOTENCY_KEY_REUSED", json.readTree(reused.body()).get("code").asString());
        assertEquals(json.readTree(current.body()), json.readTree(get("/api/owner/profile").body()));
        assertEquals(200, put("correction", "{\"enabled\":false,\"expectedVersion\":2}").statusCode());
    }

    @Test
    void operationLookupReturnsSavedSuccessAndDefiniteFailure() throws Exception {
        var success = put("success", "{\"enabled\":true,\"expectedVersion\":1}");
        var failed = put("failure", "{\"enabled\":false,\"expectedVersion\":1}");
        var successLookup = get("/api/owner/operations/success");
        assertEquals(200, successLookup.statusCode());
        assertEquals("no-store", successLookup.headers().firstValue("Cache-Control").orElseThrow());
        var savedSuccess = json.readTree(successLookup.body());
        assertEquals(200, savedSuccess.get("statusCode").asInt());
        assertEquals(json.readTree(success.body()), savedSuccess.get("profile"));
        assertFalse(savedSuccess.has("error"));
        var failureLookup = get("/api/owner/operations/failure");
        assertEquals(200, failureLookup.statusCode());
        var savedFailure = json.readTree(failureLookup.body());
        assertEquals(409, savedFailure.get("statusCode").asInt());
        assertEquals(json.readTree(failed.body()), savedFailure.get("error"));
        assertFalse(savedFailure.has("profile"));
        assertEquals(json.readTree(success.body()), json.readTree(get("/api/owner/profile").body()));
    }

    @Test
    void unavailableOperationReturnsGoneWithoutExecutingAnything() throws Exception {
        var before = get("/api/owner/profile");
        var missing = get("/api/owner/operations/lost-after-reset");
        assertEquals(410, missing.statusCode());
        assertEquals("no-store", missing.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(json.readTree("""
                {"code":"OPERATION_UNAVAILABLE","message":"Результат операции недоступен. Возможен сброс данных."}
                """), json.readTree(missing.body()));
        assertEquals(json.readTree(before.body()), json.readTree(get("/api/owner/profile").body()));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {"enabled":true,"expectedVersion":0} | expectedVersion
            {"enabled":true,"expectedVersion":-1} | expectedVersion
            {"enabled":true} | expectedVersion
            {"enabled":true,"expectedVersion":null} | expectedVersion
            {"expectedVersion":1} | enabled
            {"enabled":null,"expectedVersion":1} | enabled
            """)
    void invalidDataIsSavedAsFieldErrorWithoutChangingProfile(String body, String field) throws Exception {
        var initial = get("/api/owner/profile");
        var failed = put("invalid", body);
        assertEquals(422, failed.statusCode());
        var error = json.readTree(failed.body());
        assertEquals("VALIDATION_FAILED", error.get("code").asString());
        assertEquals(field, error.get("fieldErrors").get(0).get("field").asString());
        assertEquals("INVALID_VALUE", error.get("fieldErrors").get(0).get("code").asString());
        assertFalse(error.get("fieldErrors").get(0).get("message").asString().isBlank());
        assertEquals(json.readTree(initial.body()), json.readTree(get("/api/owner/profile").body()));
        assertEquals(json.readTree(failed.body()), json.readTree(put("invalid", body).body()));
        var saved = json.readTree(get("/api/owner/operations/invalid").body());
        assertEquals(422, saved.get("statusCode").asInt());
        assertEquals(error, saved.get("error"));
        assertEquals(409, put("invalid", "{\"enabled\":true,\"expectedVersion\":1}").statusCode());
        assertEquals(200, put("corrected", "{\"enabled\":true,\"expectedVersion\":1}").statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "[]", "", "{\"enabled\":\"true\",\"expectedVersion\":1}",
        "{\"enabled\":1,\"expectedVersion\":1}", "{\"enabled\":true,\"expectedVersion\":1.5}",
        "{\"enabled\":true,\"expectedVersion\":\"1\"}",
        "{\"enabled\":true,\"expectedVersion\":9223372036854775808}"})
    void malformedRequestsReturnSavedDefiniteFailureAndNeverChangeState(String body) throws Exception {
        var initial = get("/api/owner/profile");
        var failed = put("malformed", body);
        assertEquals(400, failed.statusCode());
        assertEquals("MALFORMED_REQUEST", json.readTree(failed.body()).get("code").asString());
        assertEquals(json.readTree(initial.body()), json.readTree(get("/api/owner/profile").body()));
        var replay = put("malformed", body);
        assertEquals(400, replay.statusCode());
        assertEquals(json.readTree(failed.body()), json.readTree(replay.body()));
        var saved = get("/api/owner/operations/malformed");
        assertEquals(200, saved.statusCode());
        assertEquals(400, json.readTree(saved.body()).get("statusCode").asInt());
        assertEquals(json.readTree(failed.body()), json.readTree(saved.body()).get("error"));
        var reused = put("malformed", "{\"enabled\":true,\"expectedVersion\":1}");
        assertEquals(409, reused.statusCode());
        assertEquals("IDEMPOTENCY_KEY_REUSED", json.readTree(reused.body()).get("code").asString());
        assertEquals(200, put("new-key", "{\"enabled\":true,\"expectedVersion\":1}").statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "A0_-"})
    void transportSafeBoundaryKeysRecoverSavedOutcomes(String key) throws Exception {
        if (key.equals("A0_-")) {
            key = key.repeat(32);
        }
        var result = put(key, "{\"enabled\":true,\"expectedVersion\":1}");
        assertEquals(200, result.statusCode());
        var lookup = get("/api/owner/operations/" + key);
        assertEquals(200, lookup.statusCode());
        assertEquals(200, json.readTree(lookup.body()).get("statusCode").asInt());
        assertEquals(json.readTree(result.body()), json.readTree(lookup.body()).get("profile"));
        assertEquals(json.readTree(result.body()), json.readTree(get("/api/owner/profile").body()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"review/key", "key.with.dot", "key space", "a"})
    void incompatibleKeyCannotChangeStateOrSaveOutcome(String key) throws Exception {
        if (key.equals("a")) {
            key = "a".repeat(129);
        }
        var initial = get("/api/owner/profile");
        var failed = put(key, "{\"enabled\":true,\"expectedVersion\":1}");
        assertEquals(400, failed.statusCode());
        assertEquals("MALFORMED_REQUEST", json.readTree(failed.body()).get("code").asString());
        assertEquals(json.readTree(initial.body()), json.readTree(get("/api/owner/profile").body()));
        var malformed = put(key, "{");
        assertEquals(400, malformed.statusCode());
        assertEquals("MALFORMED_REQUEST", json.readTree(malformed.body()).get("code").asString());
        assertEquals(json.readTree(initial.body()), json.readTree(get("/api/owner/profile").body()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void idempotencyKeyIsRequiredAndMustNotBeBlank(String key) throws Exception {
        var initial = get("/api/owner/profile");
        var failed = put(key, "{\"enabled\":true,\"expectedVersion\":1}");
        assertEquals(400, failed.statusCode());
        assertEquals("MALFORMED_REQUEST", json.readTree(failed.body()).get("code").asString());
        assertEquals(json.readTree(initial.body()), json.readTree(get("/api/owner/profile").body()));
    }

    @Test
    void concurrentCommandsForSameVersionHaveOneWinnerAndNoPartialChanges() throws Exception {
        var gate = new CyclicBarrier(3);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                gate.await();
                return put("concurrent-1", "{\"enabled\":true,\"expectedVersion\":1}");
            });
            var second = executor.submit(() -> {
                gate.await();
                return put("concurrent-2", "{\"enabled\":true,\"expectedVersion\":1}");
            });
            gate.await();
            var responses = List.of(first.get(), second.get());
            assertEquals(List.of(200, 409), responses.stream().map(HttpResponse::statusCode).sorted().toList());
            var winner = responses.stream().filter(response -> response.statusCode() == 200).findFirst().orElseThrow();
            assertEquals(2, json.readTree(winner.body()).get("version").asInt());
            assertEquals(json.readTree(winner.body()), json.readTree(get("/api/owner/profile").body()));
            var loser = responses.stream().filter(response -> response.statusCode() == 409).findFirst().orElseThrow();
            assertEquals("RESOURCE_CHANGED", json.readTree(loser.body()).get("code").asString());
            assertEquals(json.readTree(winner.body()), json.readTree(loser.body()).get("currentProfile"));
        }
    }

    @Test
    void concurrentRepeatsOfSameKeyExecuteOnce() throws Exception {
        var gate = new CyclicBarrier(3);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                gate.await();
                return put("concurrent", "{\"enabled\":true,\"expectedVersion\":1}");
            });
            var second = executor.submit(() -> {
                gate.await();
                return put("concurrent", "{\"enabled\":true,\"expectedVersion\":1}");
            });
            gate.await();
            var a = first.get();
            var b = second.get();
            assertEquals(200, a.statusCode());
            assertEquals(200, b.statusCode());
            assertEquals(json.readTree(a.body()), json.readTree(b.body()));
            assertEquals(2, json.readTree(get("/api/owner/profile").body()).get("version").asInt());
            assertEquals(json.readTree(a.body()),
                    json.readTree(get("/api/owner/operations/concurrent").body()).get("profile"));
        }
    }

    @Test
    void largestRepresentableVersionIsAStateConflictNotAMalformedNumber() throws Exception {
        var response = put("max", "{\"enabled\":true,\"expectedVersion\":9223372036854775807}");
        assertEquals(409, response.statusCode());
        assertEquals("RESOURCE_CHANGED", json.readTree(response.body()).get("code").asString());
        assertEquals(1, json.readTree(get("/api/owner/profile").body()).get("version").asInt());
    }

    @Test
    void nonJsonCommandIsARecoverableFormatFailure() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var failed = client.send(HttpRequest.newBuilder(uri("/api/owner/public-booking"))
                    .header("Content-Type", "text/plain").header("Idempotency-Key", "not-json")
                    .PUT(HttpRequest.BodyPublishers.ofString("not json")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(400, failed.statusCode());
            assertEquals("MALFORMED_REQUEST", json.readTree(failed.body()).get("code").asString());
            var saved = get("/api/owner/operations/not-json");
            assertEquals(200, saved.statusCode());
            assertEquals(400, json.readTree(saved.body()).get("statusCode").asInt());
            assertEquals(json.readTree(failed.body()), json.readTree(saved.body()).get("error"));
            assertEquals(1, json.readTree(get("/api/owner/profile").body()).get("version").asInt());
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    private HttpResponse<String> put(String key, String body) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(uri("/api/owner/public-booking"))
                    .header("Content-Type", "application/json");
            if (key != null) {
                request.header("Idempotency-Key", key);
            }
            return client.send(request.PUT(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
