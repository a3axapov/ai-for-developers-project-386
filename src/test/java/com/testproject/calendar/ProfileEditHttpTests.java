package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProfileEditHttpTests {

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper json;

    @Test
    void nameEditNormalizesSpacesPreservesAddressAndBookingSwitch() throws Exception {
        assertEquals(200, put("/api/owner/public-booking", "enable",
                "{\"enabled\":true,\"expectedVersion\":1}").statusCode());
        var result = edit("name", "  Анна\u00a0  李 😀  ", " OWNER ", 2);
        assertEquals(200, result.statusCode());
        assertEquals("no-store", result.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(json.readTree("""
                {"name":"Анна 李 😀","username":"owner","timeZone":"Europe/Moscow",
                 "bookingEnabled":true,"version":3,"bookingUrl":"/booking/owner"}
                """), json.readTree(result.body()));
        assertEquals(json.readTree(result.body()), json.readTree(get("/api/owner/profile").body()));
        assertEquals("Анна 李 😀", json.readTree(get("/api/public/owner").body()).get("ownerName").asString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "LONG", "\tAnna", "Anna\n", "\u0000", "\u007f", "\u0085",
        "\u200b", "\u200d", "\u202e", "\u2060", "\uFEFF", "\u034f", "\u2028", "\u2029", "\u0378"})
    void invalidNameReturnsFieldErrorAndChangesNothing(String name) throws Exception {
        if (name.equals("LONG")) {
            name = "😀".repeat(101);
        }
        var before = get("/api/owner/profile");
        var result = edit("invalid-name", name, "new-owner", 1);
        assertEquals(422, result.statusCode());
        var error = json.readTree(result.body());
        assertEquals("VALIDATION_FAILED", error.get("code").asString());
        assertEquals("name", error.get("fieldErrors").get(0).get("field").asString());
        assertEquals("INVALID_VALUE", error.get("fieldErrors").get(0).get("code").asString());
        assertEquals(json.readTree(before.body()), json.readTree(get("/api/owner/profile").body()));
        assertEquals(200, get("/api/public/owner").statusCode());
        assertEquals(404, get("/api/public/new-owner").statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "ab", "LONG", "-abc", "abc-", "a b", "аbc", "a_b", "abc.def",
        "\tabc", "abc\n", "\u200babc", "abc\u034f", "abc\u2028", "abc\u0000"})
    void invalidUsernameReturnsFieldErrorWithoutChangingNameOrLinks(String username) throws Exception {
        if (username.equals("LONG")) {
            username = "a".repeat(31);
        }
        var before = get("/api/owner/profile");
        var result = edit("invalid-user", "Новое имя", username, 1);
        assertEquals(422, result.statusCode());
        var error = json.readTree(result.body());
        assertEquals("VALIDATION_FAILED", error.get("code").asString());
        assertEquals("username", error.get("fieldErrors").get(0).get("field").asString());
        assertEquals(json.readTree(before.body()), json.readTree(get("/api/owner/profile").body()));
        assertEquals(200, get("/api/public/owner").statusCode());
    }

    @Test
    void editsCheckVersionAndReplaySavedOutcomesWithoutReapplying() throws Exception {
        var first = edit("first", "Первое", "first", 1);
        assertEquals(200, first.statusCode());
        var stale = edit("stale", "Потерянное", "lost", 1);
        assertEquals(409, stale.statusCode());
        assertEquals("RESOURCE_CHANGED", json.readTree(stale.body()).get("code").asString());
        assertEquals(json.readTree(first.body()), json.readTree(stale.body()).get("currentProfile"));
        var second = edit("second", "Второе", "second", 2);
        assertEquals(200, second.statusCode());
        assertEquals(json.readTree(first.body()), json.readTree(edit("first", "Первое", "first", 1).body()));
        assertEquals(json.readTree(stale.body()), json.readTree(edit("stale", "Потерянное", "lost", 1).body()));
        var reused = edit("first", "Другое", "other", 3);
        assertEquals(409, reused.statusCode());
        assertEquals("IDEMPOTENCY_KEY_REUSED", json.readTree(reused.body()).get("code").asString());
        var crossCommand = put("/api/owner/public-booking", "first", "{\"enabled\":true,\"expectedVersion\":3}");
        assertEquals(409, crossCommand.statusCode());
        assertEquals("IDEMPOTENCY_KEY_REUSED", json.readTree(crossCommand.body()).get("code").asString());
        assertEquals(json.readTree(second.body()), json.readTree(get("/api/owner/profile").body()));
        var saved = json.readTree(get("/api/owner/operations/first").body());
        assertEquals(200, saved.get("statusCode").asInt());
        assertEquals(json.readTree(first.body()), saved.get("profile"));
        assertEquals(json.readTree(stale.body()),
                json.readTree(get("/api/owner/operations/stale").body()).get("error"));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            {"username":"valid","expectedVersion":1} | name
            {"name":null,"username":"valid","expectedVersion":1} | name
            {"name":"Name","expectedVersion":1} | username
            {"name":"Name","username":null,"expectedVersion":1} | username
            {"name":"Name","username":"valid"} | expectedVersion
            {"name":"Name","username":"valid","expectedVersion":null} | expectedVersion
            {"name":"Name","username":"valid","expectedVersion":0} | expectedVersion
            {"name":"Name","username":"valid","expectedVersion":-1} | expectedVersion
            """)
    void missingOrInvalidFieldsAreSavedDefiniteValidationFailures(String body, String field) throws Exception {
        var before = get("/api/owner/profile");
        var failed = put("/api/owner/profile", "invalid", body);
        assertEquals(422, failed.statusCode());
        assertEquals(field, json.readTree(failed.body()).get("fieldErrors").get(0).get("field").asString());
        assertEquals(json.readTree(failed.body()), json.readTree(put("/api/owner/profile", "invalid", body).body()));
        var saved = json.readTree(get("/api/owner/operations/invalid").body());
        assertEquals(422, saved.get("statusCode").asInt());
        assertEquals(json.readTree(failed.body()), saved.get("error"));
        assertEquals(409, edit("invalid", "Исправлено", "valid", 1).statusCode());
        assertEquals(json.readTree(before.body()), json.readTree(get("/api/owner/profile").body()));
        assertEquals(200, edit("corrected", "Исправлено", "valid", 1).statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "[]", "null", "", "{\"name\":4,\"username\":\"valid\",\"expectedVersion\":1}",
        "{\"name\":\"Name\",\"username\":true,\"expectedVersion\":1}",
        "{\"name\":\"Name\",\"username\":\"valid\",\"expectedVersion\":1.5}",
        "{\"expectedVersion\":\"1\"}", "{\"expectedVersion\":9223372036854775808}"})
    void malformedEditsAreRecoverableAndDoNotChangeState(String body) throws Exception {
        var before = get("/api/owner/profile");
        var failed = put("/api/owner/profile", "malformed", body);
        assertEquals(400, failed.statusCode());
        assertEquals("MALFORMED_REQUEST", json.readTree(failed.body()).get("code").asString());
        var replay = put("/api/owner/profile", "malformed", body);
        assertEquals(400, replay.statusCode());
        assertEquals(json.readTree(failed.body()), json.readTree(replay.body()));
        var saved = json.readTree(get("/api/owner/operations/malformed").body());
        assertEquals(400, saved.get("statusCode").asInt());
        assertEquals(json.readTree(failed.body()), saved.get("error"));
        assertEquals(409, edit("malformed", "Name", "valid", 1).statusCode());
        assertEquals(json.readTree(before.body()), json.readTree(get("/api/owner/profile").body()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0-9", "LONG", "booking-entry"})
    void boundaryNamesAndUsernamesReplaceLinksAndCanRestoreOldAddress(String username) throws Exception {
        if (username.equals("LONG")) {
            username = "a".repeat(30);
        }
        var changed = edit("change", "😀".repeat(100),
                "\u2002" + username.toUpperCase(java.util.Locale.ROOT) + "\u00a0", 1);
        assertEquals(200, changed.statusCode());
        var profile = json.readTree(changed.body());
        assertEquals("😀".repeat(100), profile.get("name").asString());
        assertEquals(username, profile.get("username").asString());
        assertEquals(false, profile.get("bookingEnabled").asBoolean());
        assertEquals("/booking/" + username, profile.get("bookingUrl").asString());
        assertEquals("/booking/" + username,
                json.readTree(get("/api/public/booking-entry/current").body()).get("bookingUrl").asString());
        assertEquals(200, get("/api/public/" + username).statusCode());
        var old = get("/api/public/owner");
        assertEquals(404, old.statusCode());
        assertEquals("PAGE_NOT_FOUND", json.readTree(old.body()).get("code").asString());
        assertEquals(false, old.headers().firstValue("Location").isPresent());
        assertEquals(200, edit("restore", "李", "owner", 2).statusCode());
        assertEquals("李", json.readTree(get("/api/public/owner").body()).get("ownerName").asString());
        assertEquals(404, get("/api/public/" + username).statusCode());
    }

    @Test
    void concurrentEditAndBookingSwitchShareOneVersionWithoutPartialChanges() throws Exception {
        var gate = new CyclicBarrier(3);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var edit = executor.submit(() -> {
                gate.await();
                return edit("edit", "Новое", "new-owner", 1);
            });
            var toggle = executor.submit(() -> {
                gate.await();
                return put("/api/owner/public-booking", "toggle", "{\"enabled\":true,\"expectedVersion\":1}");
            });
            gate.await();
            var responses = List.of(edit.get(), toggle.get());
            assertEquals(List.of(200, 409), responses.stream().map(HttpResponse::statusCode).sorted().toList());
            var winner = responses.stream().filter(r -> r.statusCode() == 200).findFirst().orElseThrow();
            var loser = responses.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow();
            assertEquals(json.readTree(winner.body()), json.readTree(loser.body()).get("currentProfile"));
            assertEquals(json.readTree(winner.body()), json.readTree(get("/api/owner/profile").body()));
            assertEquals(2, json.readTree(winner.body()).get("version").asInt());
            if (edit.get().statusCode() == 200) {
                assertEquals(json.readTree("""
                        {"name":"Новое","username":"new-owner","timeZone":"Europe/Moscow",
                         "bookingEnabled":false,"version":2,"bookingUrl":"/booking/new-owner"}
                        """), json.readTree(winner.body()));
            } else {
                assertEquals(json.readTree("""
                        {"name":"Владелец календаря","username":"owner","timeZone":"Europe/Moscow",
                         "bookingEnabled":true,"version":2,"bookingUrl":"/booking/owner"}
                        """), json.readTree(winner.body()));
            }
        }
    }

    @Test
    void malformedCommandsOnDifferentEndpointsCannotShareAnOperationKey() throws Exception {
        var malformed = put("/api/owner/profile", "shared", "{");
        assertEquals(400, malformed.statusCode());
        var reused = put("/api/owner/public-booking", "shared", "{");
        assertEquals(409, reused.statusCode());
        assertEquals("IDEMPOTENCY_KEY_REUSED", json.readTree(reused.body()).get("code").asString());
        assertEquals(json.readTree(malformed.body()),
                json.readTree(get("/api/owner/operations/shared").body()).get("error"));
    }

    @Test
    void genuinelyConcurrentRepeatedEditExecutesOnlyOnce() throws Exception {
        var gate = new CyclicBarrier(3);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                gate.await();
                return edit("same", "Имя", "new-owner", 1);
            });
            var second = executor.submit(() -> {
                gate.await();
                return edit("same", "Имя", "new-owner", 1);
            });
            gate.await();
            var a = first.get();
            var b = second.get();
            assertEquals(200, a.statusCode());
            assertEquals(200, b.statusCode());
            assertEquals(json.readTree(a.body()), json.readTree(b.body()));
            assertEquals(2, json.readTree(get("/api/owner/profile").body()).get("version").asInt());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "bad/key", "key space", "LONG"})
    void invalidKeysCannotEditProfile(String key) throws Exception {
        if (key.equals("LONG")) {
            key = "a".repeat(129);
        }
        assertEquals(400, edit(key, "Имя", "new-owner", 1).statusCode());
        assertEquals(1, json.readTree(get("/api/owner/profile").body()).get("version").asInt());
        assertEquals(400, edit(null, "Имя", "new-owner", 1).statusCode());
    }

    private HttpResponse<String> edit(String key, String name, String username, long version) throws Exception {
        return put("/api/owner/profile", key, json.writeValueAsString(
                Map.of("name", name, "username", username, "expectedVersion", version)));
    }

    private HttpResponse<String> get(String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private HttpResponse<String> put(String path, String key, String body) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json");
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
