package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import tools.jackson.databind.json.JsonMapper;

class InitialConfigurationTests {

    @TempDir
    private Path temporary;

    @ParameterizedTest
    @MethodSource("validConfiguration")
    void readsOverriddenYamlAndNormalizesProfile(String name, String username, String expectedName,
            String expectedUsername) throws Exception {
        var configuration = temporary.resolve("owner.yaml");
        Files.writeString(configuration, """
                calendar:
                  initial:
                    name: '%s'
                    username: '%s'
                    time-zone: Pacific/Auckland
                    work-start: '00:00'
                    work-end: '24:00'
                """.formatted(name, username));
        try (var application = new SpringApplicationBuilder(CalendarApplication.class)
                .run("--server.port=0", "--spring.config.additional-location=" + configuration.toUri());
                var client = HttpClient.newHttpClient()) {
            int port = ((WebServerApplicationContext) application).getWebServer().getPort();
            var response = client.send(HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/api/owner/profile")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            var profile = JsonMapper.builder().build().readTree(response.body());
            assertEquals(expectedName, profile.get("name").asString());
            assertEquals(expectedUsername, profile.get("username").asString());
            assertEquals("Pacific/Auckland", profile.get("timeZone").asString());
            assertEquals("/booking/" + expectedUsername, profile.get("bookingUrl").asString());
        }
    }

    static String[][] validConfiguration() {
        return new String[][] {
            {"  Анна   Петрова  ", "  ANNA-1  ", "Анна Петрова", "anna-1"},
            {"Я", "abc", "Я", "abc"},
            {"😀".repeat(100), "a".repeat(30), "😀".repeat(100), "a".repeat(30)}
        };
    }

    @Test
    void rejectsInvalidUsernameAtStartup() {
        rejectsConfiguration("username", "-invalid");
    }

    @ParameterizedTest
    @MethodSource("invalidConfiguration")
    void rejectsInvalidConfigurationAtStartup(String property, String value) {
        rejectsConfiguration(property, value);
    }

    static String[][] invalidConfiguration() {
        return new String[][] {
            {"name", "   "},
            {"name", "a".repeat(101)},
            {"name", "Owner\nName"},
            {"name", "Owner\u200bName"},
            {"username", "ab"},
            {"username", "a".repeat(31)},
            {"username", "owner-"},
            {"username", "владелец"},
            {"username", "\towner"},
            {"time-zone", "Mars/Olympus"},
            {"time-zone", "+03:00"},
            {"working-days", "NOT_A_DAY"},
            {"work-start", "24:00"},
            {"work-start", "09:00:30"},
            {"work-start", "18:00"},
            {"work-end", "08:59"},
            {"work-end", "25:00"},
            {"booking-enabled", "sometimes"}
        };
    }

    private void rejectsConfiguration(String property, String value) {
        var failure = assertThrows(Exception.class, () -> {
            try (var application = new SpringApplicationBuilder(CalendarApplication.class)
                    .run("--server.port=0", "--calendar.initial." + property + "=" + value)) {
                // A successful startup here is a test failure, but still closes the server.
            }
        });
        assertTrue(failureText(failure).contains("calendar.initial." + property), failureText(failure));
    }

    private static String failureText(Throwable failure) {
        var messages = new StringBuilder();
        while (failure != null) {
            messages.append(failure.getMessage()).append('\n');
            failure = failure.getCause();
        }
        return messages.toString();
    }
}
