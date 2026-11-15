package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CalendarExportHttpTests {
    private final JsonMapper json = JsonMapper.builder().build();
    private static final String TITLE = "Разбор 😀, точка; путь\\ " + "李😀".repeat(12);
    private static final String DESCRIPTION = "Подробное описание, с точкой; и слешем\\\r\n"
            + "STATUS:CANCELLED\r\nATTENDEE:mailto:injected@example.com\n" + "Юникод 😀 李 ".repeat(35);

    @Test
    void sameLinkExportsCurrentStatusTimeSavedConditionsAndCurrentOwnerEvenWhenPastArchivedAndDisabled()
            throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        try (var app = start(clock)) {
            var saved = book(app, true);
            assertEquals(json.readTree("[\"cancel\",\"export\"]"), get(app, path(saved)).get("allowedActions"));
            var original = parse(send(app, "GET", path(saved) + "/calendar.ics", null).body());
            var ownerPath = "/api/owner/bookings/" + saved.get("booking").get("id").asString();
            assertEquals(200, send(app, "POST", ownerPath + "/confirm", "{\"expectedVersion\":1}").statusCode());
            var confirmed = parse(send(app, "GET", path(saved) + "/calendar.ics", null).body());
            assertEquals("CONFIRMED", one(confirmed, "STATUS"));
            assertEquals(one(original, "UID"), one(confirmed, "UID"));
            assertEquals(200, send(app, "PUT", "/api/owner/meeting-types/Meeting", json.writeValueAsString(Map.of(
                    "title", "Изменённое название", "description", "Совершенно новое описание встречи",
                    "durationMinutes", 45, "requiresApproval", false, "expectedVersion", 1))).statusCode());
            assertEquals(200, send(app, "PUT", "/api/owner/profile",
                    "{\"name\":\"Новое имя 李\",\"username\":\"new-owner\",\"expectedVersion\":1}").statusCode());
            var moved = send(app, "POST", ownerPath + "/reschedule",
                    "{\"startsAt\":\"2026-12-07T09:15:00+03:00\",\"expectedVersion\":2}");
            assertEquals(200, moved.statusCode(), moved.body());
            assertEquals(200, send(app, "POST", "/api/owner/meeting-types/Meeting/archive",
                    "{\"expectedVersion\":2}").statusCode());
            assertEquals(200, send(app, "PUT", "/api/owner/public-booking",
                    "{\"enabled\":false,\"expectedVersion\":2}").statusCode());
            clock.set("2026-12-08T00:00:00Z");
            var response = send(app, "GET", path(saved) + "/calendar.ics", null);
            assertEquals(200, response.statusCode(), response.body());
            var current = parse(response.body());
            assertEquals(one(original, "SUMMARY"), one(current, "SUMMARY"));
            assertEquals(saved.get("booking").get("description").asString() + "\nВладелец: Новое имя 李",
                    text(one(current, "DESCRIPTION")));
            assertEquals("20261207T061500Z", one(current, "DTSTART"));
            assertEquals("20261207T064500Z", one(current, "DTEND"));
            assertEquals("20261208T000000Z", one(current, "DTSTAMP"));
            assertEquals("CONFIRMED", one(current, "STATUS"));
            assertEquals(one(original, "UID"), one(current, "UID"));
            assertEquals(one(original, "URL"), one(current, "URL"));
            assertEquals(json.readTree("[\"export\"]"), get(app, path(saved)).get("allowedActions"));
        }
    }

    @Test
    void terminalExportsAreForbiddenAndUnknownOrBookingIdLinksAreNeutral() throws Exception {
        for (String action : List.of("reject", "cancel", "expire")) {
            var clock = new BookingSubmissionHttpTests.MutableClock();
            try (var app = start(clock)) {
                var saved = book(app, !action.equals("cancel"));
                if (action.equals("expire")) {
                    clock.set("2026-11-02T05:59:59.999999999Z");
                    assertEquals(200, send(app, "GET", path(saved) + "/calendar.ics", null).statusCode());
                    clock.set("2026-11-02T06:00:00Z");
                } else {
                    assertEquals(200, send(app, "POST", "/api/owner/bookings/"
                            + saved.get("booking").get("id").asString() + "/" + action,
                            "{\"expectedVersion\":1}").statusCode());
                }
                var response = send(app, "GET", path(saved) + "/calendar.ics", null);
                assertEquals(409, response.statusCode(), response.body());
                assertEquals("ACTION_NOT_ALLOWED", json.readTree(response.body()).get("code").asString());
                assertEquals(get(app, path(saved)), json.readTree(response.body()).get("currentBooking"));
                privacy(response);
                assertFalse(response.headers().firstValue("Content-Disposition").isPresent());
                assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
                if (action.equals("expire")) {
                    assertEquals("APPROVAL_EXPIRED", get(app, path(saved)).get("rejectionReason").asString());
                }
                var unknown = send(app, "GET", "/api/bookings/unknown/calendar.ics", null);
                var idInsteadOfSecret = send(app, "GET", "/api/bookings/"
                        + saved.get("booking").get("id").asString() + "/calendar.ics", null);
                assertEquals(404, unknown.statusCode());
                assertEquals(404, idInsteadOfSecret.statusCode());
                assertEquals(unknown.body(), idInsteadOfSecret.body());
                assertEquals(json.readTree("{\"code\":\"BOOKING_NOT_FOUND\",\"message\":\"Бронирование не найдено\"}"),
                        json.readTree(unknown.body()));
                privacy(unknown);
                privacy(idInsteadOfSecret);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
        ",,http://localhost:8080",
        ",https://render.example,https://render.example",
        "https://explicit.example,https://render.example,https://explicit.example",
        "https://explicit.example,invalid-render-origin,https://explicit.example"
    })
    void yamlEnvironmentOriginIgnoresSpoofedHostAndForwardingHeaders(
            String explicitOrigin, String renderOrigin, String expectedOrigin) throws Exception {
        var variables = new java.util.HashMap<String, Object>();
        if (explicitOrigin != null) {
            variables.put("CALENDAR_PUBLIC_ORIGIN", explicitOrigin);
        }
        if (renderOrigin != null) {
            variables.put("RENDER_EXTERNAL_URL", renderOrigin);
        }
        var clock = new BookingSubmissionHttpTests.MutableClock();
        // No calendar.public-origin override: exercise application.yaml's real placeholder chain.
        try (var app = new SpringApplicationBuilder(CalendarApplication.class)
                .environment(PublicOriginConfigurationTests.environment(variables))
                .initializers(context -> context.getBeanFactory().registerSingleton("testClock", clock))
                .run("--server.port=0", "--calendar.initial.booking-enabled=true")) {
            var saved = book(app, false);
            int port = ((WebServerApplicationContext) app).getWebServer().getPort();
            // Raw HTTP permits testing Host without changing JVM-wide restricted-header settings.
            try (var socket = new java.net.Socket("127.0.0.1", port)) {
                socket.setSoTimeout(10000);
                socket.getOutputStream().write(("GET " + path(saved) + "/calendar.ics HTTP/1.0\r\n"
                        + "Host: attacker.example\r\nForwarded: host=attacker.example;proto=http\r\n"
                        + "X-Forwarded-Host: attacker.example\r\nX-Forwarded-Proto: http\r\n"
                        + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                var response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(response.startsWith("HTTP/1.1 200"), response);
                var event = parse(response.substring(response.indexOf("\r\n\r\n") + 4));
                assertEquals(expectedOrigin + saved.get("resultUrl").asString(), one(event, "URL"));
                assertFalse(response.contains("attacker.example"));
            }
        }
    }

    @Test
    void dstFoldAndRescheduleRepresentActualInstantsAndSavedElapsedDuration() throws Exception {
        var clock = new BookingSubmissionHttpTests.MutableClock();
        clock.set("2026-10-20T00:00:00Z");
        try (var app = new SpringApplicationBuilder(CalendarApplication.class)
                .initializers(context -> context.getBeanFactory().registerSingleton("testClock", clock))
                .run("--server.port=0", "--calendar.initial.booking-enabled=true",
                        "--calendar.public-origin=http://127.0.0.1:8080",
                        "--calendar.initial.time-zone=Europe/Berlin", "--calendar.initial.working-days=SUNDAY",
                        "--calendar.initial.work-start=00:00", "--calendar.initial.work-end=24:00")) {
            var saved = book(app, false, "2026-10-25T02:45:00+02:00");
            var before = parse(send(app, "GET", path(saved) + "/calendar.ics", null).body());
            assertEquals("20261025T004500Z", one(before, "DTSTART"));
            assertEquals("20261025T011500Z", one(before, "DTEND"));
            assertEquals(200, send(app, "POST", "/api/owner/bookings/"
                    + saved.get("booking").get("id").asString() + "/reschedule",
                    "{\"startsAt\":\"2026-10-25T02:15:00+01:00\",\"expectedVersion\":1}").statusCode());
            var after = parse(send(app, "GET", path(saved) + "/calendar.ics", null).body());
            assertEquals("20261025T011500Z", one(after, "DTSTART"));
            assertEquals("20261025T014500Z", one(after, "DTEND"));
            assertEquals(one(before, "UID"), one(after, "UID"));
        }
    }

    @Test
    void genuinelyConcurrentExportsAndCommandsProduceOnlyCompleteSnapshots() throws Exception {
        for (String action : List.of("confirm", "cancel", "reschedule")) {
            try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
                var saved = book(app, action.equals("confirm"));
                var ready = new java.util.concurrent.CountDownLatch(2);
                var go = new java.util.concurrent.CountDownLatch(1);
                try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                    var export = pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return send(app, "GET", path(saved) + "/calendar.ics", null);
                    });
                    var change = pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return send(app, "POST", "/api/owner/bookings/"
                                + saved.get("booking").get("id").asString() + "/" + action,
                                action.equals("reschedule")
                                        ? "{\"startsAt\":\"2026-12-07T10:00:00+03:00\",\"expectedVersion\":1}"
                                        : "{\"expectedVersion\":1}");
                    });
                    assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
                    go.countDown();
                    var response = export.get(20, java.util.concurrent.TimeUnit.SECONDS);
                    assertEquals(200, change.get(20, java.util.concurrent.TimeUnit.SECONDS).statusCode());
                    if (response.statusCode() == 409) {
                        assertEquals("cancel", action);
                        assertEquals("ACTION_NOT_ALLOWED", json.readTree(response.body()).get("code").asString());
                    } else {
                        assertEquals(200, response.statusCode());
                        var event = parse(response.body());
                        assertTrue(Set.of("TENTATIVE", "CONFIRMED").contains(one(event, "STATUS")));
                        assertTrue(Set.of(List.of("20261102T060000Z", "20261102T063000Z"),
                                List.of("20261207T070000Z", "20261207T073000Z"))
                                .contains(List.of(one(event, "DTSTART"), one(event, "DTEND"))));
                        if (!action.equals("reschedule")) {
                            assertEquals("20261102T060000Z", one(event, "DTSTART"));
                        }
                    }
                    var current = get(app, path(saved));
                    assertEquals(2, current.get("version").asInt());
                    assertEquals(action.equals("cancel") ? "CANCELLED" : "CONFIRMED", current.get("status").asString());
                    assertEquals(action.equals("reschedule") ? "2026-12-07T10:00:00+03:00"
                            : "2026-11-02T09:00:00+03:00", current.get("startsAt").asString());
                }
            }
        }
    }

    @Test
    void pendingCalendarIsPrivateUtf8Rfc5545SnapshotWithoutGuestContacts() throws Exception {
        try (var app = start(new BookingSubmissionHttpTests.MutableClock())) {
            var saved = book(app, true);
            var response = send(app, "GET", path(saved) + "/calendar.ics", null);
            assertEquals(200, response.statusCode(), response.body());
            assertEquals("text/calendar;charset=utf-8",
                    response.headers().firstValue("Content-Type").orElseThrow().replace(" ", "").toLowerCase());
            assertEquals("attachment; filename=\"booking.ics\"",
                    response.headers().firstValue("Content-Disposition").orElseThrow());
            privacy(response);
            var event = parse(response.body());
            assertEquals(Set.of("BEGIN", "VERSION", "PRODID", "CALSCALE", "UID", "DTSTAMP", "DTSTART",
                    "DTEND", "SUMMARY", "DESCRIPTION", "STATUS", "URL", "END"), event.keySet());
            assertEquals("2.0", one(event, "VERSION"));
            assertEquals("GREGORIAN", one(event, "CALSCALE"));
            assertEquals(List.of("VCALENDAR", "VEVENT"), event.get("BEGIN"));
            assertEquals(List.of("VEVENT", "VCALENDAR"), event.get("END"));
            assertFalse(one(event, "UID").isBlank());
            assertEquals("20261101T000000Z", one(event, "DTSTAMP"));
            assertEquals("20261102T060000Z", one(event, "DTSTART"));
            assertEquals("20261102T063000Z", one(event, "DTEND"));
            assertEquals(saved.get("booking").get("title").asString(), text(one(event, "SUMMARY")));
            assertEquals(saved.get("booking").get("description").asString()
                    + "\nВладелец: Владелец календаря", text(one(event, "DESCRIPTION")));
            assertEquals("TENTATIVE", one(event, "STATUS"));
            assertEquals("https://calendar.example" + saved.get("resultUrl").asString(), one(event, "URL"));
            for (String privateValue : List.of("GuestSecretSentinel", "private@example.com", "CommentSecretSentinel")) {
                assertFalse(response.body().contains(privateValue));
            }
            // Merely viewing either result or export never mutates the booking.
            assertEquals(saved.get("booking"), get(app, path(saved)));
        }
    }

    // A strict, independent reader for this RFC 5545 profile: validate bytes/physical lines,
    // unfold before parsing properties, then decode TEXT with a state machine.
    private Map<String, List<String>> parse(String body) throws Exception {
        assertTrue(body.endsWith("\r\n"));
        assertFalse(body.replace("\r\n", "").contains("\n"));
        assertFalse(body.replace("\r\n", "").contains("\r"));
        for (var line : body.split("\r\n")) {
            var bytes = line.getBytes(StandardCharsets.UTF_8);
            assertTrue(bytes.length <= 75, "Physical line exceeds 75 octets: " + bytes.length);
            assertEquals(line, StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
            assertFalse(line.contains("\uFFFD"));
        }
        var result = new LinkedHashMap<String, List<String>>();
        for (var line : body.replaceAll("\r\n[ \\t]", "").split("\r\n")) {
            int colon = line.indexOf(':');
            assertTrue(colon > 0, line);
            result.computeIfAbsent(line.substring(0, colon), ignored -> new ArrayList<>())
                    .add(line.substring(colon + 1));
        }
        return result;
    }

    private String one(Map<String, List<String>> event, String key) {
        assertEquals(1, event.get(key).size(), key);
        return event.get(key).getFirst();
    }

    private String text(String value) {
        var decoded = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') {
                assertTrue(++i < value.length());
                char next = value.charAt(i);
                assertTrue("\\,;nN".indexOf(next) >= 0);
                decoded.append(next == 'n' || next == 'N' ? '\n' : next);
            } else {
                assertTrue(c != ',' && c != ';', "Unescaped TEXT punctuation");
                decoded.append(c);
            }
        }
        return decoded.toString();
    }

    private void privacy(HttpResponse<String> response) {
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("no-referrer", response.headers().firstValue("Referrer-Policy").orElseThrow());
    }

    private ConfigurableApplicationContext start(BookingSubmissionHttpTests.MutableClock clock) {
        return new SpringApplicationBuilder(CalendarApplication.class)
                .initializers(context -> context.getBeanFactory().registerSingleton("testClock", clock))
                .run("--server.port=0", "--calendar.initial.booking-enabled=true",
                        "--calendar.public-origin=https://calendar.example");
    }

    private JsonNode book(ConfigurableApplicationContext app, boolean manual) throws Exception {
        return book(app, manual, "2026-11-02T09:00:00+03:00");
    }

    private JsonNode book(ConfigurableApplicationContext app, boolean manual, String startsAt) throws Exception {
        var type = send(app, "POST", "/api/owner/meeting-types", json.writeValueAsString(Map.of(
                "id", "Meeting", "title", TITLE, "description", DESCRIPTION,
                "durationMinutes", 30, "requiresApproval", manual)));
        assertEquals(201, type.statusCode(), type.body());
        var slots = get(app, "/api/public/owner/meeting-types/Meeting/slots");
        var prepared = send(app, "POST", "/api/public/owner/booking-attempts", json.writeValueAsString(Map.of(
                "meetingTypeId", "Meeting", "conditionsToken", slots.get("conditionsToken").asString(),
                "startsAt", startsAt, "guestName", "GuestSecretSentinel",
                "guestEmail", "private@example.com", "comment", "CommentSecretSentinel")));
        assertEquals(201, prepared.statusCode(), prepared.body());
        var attempt = json.readTree(prepared.body());
        var request = HttpRequest.newBuilder(uri(app, "/api/booking-attempts/"
                + attempt.get("attemptId").asString() + "/submit"))
                .header("Authorization", "Bearer " + attempt.get("attemptSecret").asString())
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        try (var client = HttpClient.newHttpClient()) {
            var submitted = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, submitted.statusCode(), submitted.body());
            return json.readTree(submitted.body());
        }
    }

    private String path(JsonNode saved) {
        return "/api" + saved.get("resultUrl").asString();
    }

    private JsonNode get(ConfigurableApplicationContext app, String path) throws Exception {
        var response = send(app, "GET", path, null);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private URI uri(ConfigurableApplicationContext app, String path) {
        int port = ((WebServerApplicationContext) app).getWebServer().getPort();
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private HttpResponse<String> send(ConfigurableApplicationContext app, String method, String path, String body)
            throws Exception {
        var builder = HttpRequest.newBuilder(uri(app, path)).header("Content-Type", "application/json");
        if (path.startsWith("/api/owner/") && !method.equals("GET")) {
            builder.header("Idempotency-Key", UUID.randomUUID().toString());
        }
        try (var client = HttpClient.newHttpClient()) {
            return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
