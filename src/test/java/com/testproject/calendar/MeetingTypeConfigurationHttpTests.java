package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MeetingTypeConfigurationHttpTests {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void customConfigurationSeedsNewTypesAndRestartResetsTypesAndOperations() throws Exception {
        try (var application = new SpringApplicationBuilder(CalendarApplication.class).run("--server.port=0",
                "--calendar.initial.time-zone=Pacific/Auckland", "--calendar.initial.working-days=FRIDAY,SUNDAY",
                "--calendar.initial.work-start=00:00", "--calendar.initial.work-end=24:00")) {
            int port = ((WebServerApplicationContext) application).getWebServer().getPort();
            var type = json.readTree(send(port, "POST", "/api/owner/meeting-types", command("First", "Встреча")));
            assertEquals("Pacific/Auckland", type.get("timeZone").asString());
            assertEquals(json.readTree("[{\"start\":\"00:00\",\"end\":\"24:00\"}]"),
                    type.get("days").get(4).get("intervals"));
            assertEquals(json.readTree("[]"), type.get("days").get(0).get("intervals"));
            assertEquals(type.get("days").get(4).get("intervals"), type.get("days").get(6).get("intervals"));
        }
        try (var application = new SpringApplicationBuilder(CalendarApplication.class).run("--server.port=0",
                "--calendar.initial.working-days=")) {
            int port = ((WebServerApplicationContext) application).getWebServer().getPort();
            try (var client = HttpClient.newHttpClient()) {
                var unavailable = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/api/owner/operations/command")).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(410, unavailable.statusCode());
                assertEquals("OPERATION_UNAVAILABLE", json.readTree(unavailable.body()).get("code").asString());
            }
            assertEquals(json.readTree("{\"items\":[]}"),
                    json.readTree(send(port, "GET", "/api/owner/meeting-types", null)));
            var type = json.readTree(send(port, "POST", "/api/owner/meeting-types", command("First", "Встреча")));
            for (var day : type.get("days")) {
                assertEquals(0, day.get("intervals").size());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"same", "title", "description", "durationMinutes", "requiresApproval"})
    void conditionsTokenDependsOnShownTermsNotIdOwnerZoneHoursOrRestart(String changed) throws Exception {
        JsonNode baseline;
        try (var application = new SpringApplicationBuilder(CalendarApplication.class).run("--server.port=0",
                "--calendar.initial.booking-enabled=true")) {
            int port = ((WebServerApplicationContext) application).getWebServer().getPort();
            send(port, "POST", "/api/owner/meeting-types", command("First", "Встреча"));
            baseline = json.readTree(send(port, "GET", "/api/public/owner/meeting-types/First", null))
                    .get("meetingType").get("conditionsToken");
        }
        try (var application = new SpringApplicationBuilder(CalendarApplication.class).run("--server.port=0",
                "--calendar.initial.booking-enabled=true", "--calendar.initial.name=Другое имя",
                "--calendar.initial.username=another", "--calendar.initial.time-zone=Pacific/Auckland",
                "--calendar.initial.work-start=10:00")) {
            int port = ((WebServerApplicationContext) application).getWebServer().getPort();
            var body = command("Other", "Встреча");
            body = switch (changed) {
                case "title" -> body.replace("Встреча", "Новая встреча");
                case "description" -> body.replace("описание", "новое описание");
                case "durationMinutes" -> body.replace(":30", ":31");
                case "requiresApproval" -> body.replace("false", "true");
                default -> body;
            };
            send(port, "POST", "/api/owner/meeting-types", body);
            var token = json.readTree(send(port, "GET", "/api/public/another/meeting-types/Other", null))
                    .get("meetingType").get("conditionsToken");
            if (changed.equals("same")) {
                assertEquals(baseline, token);
            } else {
                assertNotEquals(baseline, token);
            }
        }
    }

    private String command(String id, String title) {
        return """
                {"id":"%s","title":"%s","description":"Достаточно длинное описание встречи",
                 "durationMinutes":30,"requiresApproval":false}
                """.formatted(id, title);
    }

    private String send(int port, String method, String path, String body) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .header("Content-Type", "application/json").header("Idempotency-Key", "command")
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body)).build();
            var result = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(method.equals("POST") ? 201 : 200, result.statusCode(), result.body());
            return result.body();
        }
    }
}
