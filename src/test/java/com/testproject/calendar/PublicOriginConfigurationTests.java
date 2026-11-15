package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;

class PublicOriginConfigurationTests {
    @ParameterizedTest
    @ValueSource(strings = { "", "relative", "//calendar.example", "ftp://calendar.example",
            "http://calendar.example", "http://127.0.0.1.evil.example", "https://user:secret@calendar.example",
            "https://calendar.example/path", "https://calendar.example?query", "https://calendar.example#fragment",
            "https://calendar.example:0", "https://calendar.example:65536", "https://calendar.example:",
            "https://calendar.example:-1", "https://", "https://bad host", "https://calendar.example/\r\nInjected:x",
            "http://localhost.evil", "http://2130706433", "http://127.1", "https://calendar.example/../" })
    void invalidOriginsFailStartupWithoutEchoingTheValue(String origin) {
        var failure = assertThrows(Exception.class, () -> {
            try (var app = new SpringApplicationBuilder(CalendarApplication.class)
                    .run("--server.port=0", "--calendar.public-origin=" + origin)) { }
        });
        var messages = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        assertTrue(messages.toString().contains("calendar.public-origin"));
        assertFalse(messages.toString().contains("user:secret"));
        assertFalse(messages.toString().contains("Injected:x"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://calendar.example", "https://calendar.example:8443/",
            "http://localhost:8080", "http://127.0.0.1:4174", "http://[::1]:4174" })
    void httpsAndExplicitLocalDevelopmentOriginsStart(String origin) {
        try (var app = new SpringApplicationBuilder(CalendarApplication.class)
                .run("--server.port=0", "--calendar.public-origin=" + origin)) {
            assertTrue(app.isActive());
        }
    }
}
