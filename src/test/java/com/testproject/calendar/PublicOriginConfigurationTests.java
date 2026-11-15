package com.testproject.calendar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class PublicOriginConfigurationTests {
    static StandardEnvironment environment(Map<String, Object> variables) {
        var environment = new StandardEnvironment();
        // Replace rather than supplement the host environment: absent variables must really be absent.
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
        return environment;
    }

    @ParameterizedTest
    @CsvSource({
        "RENDER_EXTERNAL_URL,relative",
        "RENDER_EXTERNAL_URL,http://calendar.example",
        "RENDER_EXTERNAL_URL,https://user:secret@calendar.example",
        "CALENDAR_PUBLIC_ORIGIN,relative"
    })
    void invalidEnvironmentOriginFailsStartupInsteadOfFallingBack(String key, String origin) {
        var variables = new java.util.HashMap<String, Object>();
        if (key.equals("CALENDAR_PUBLIC_ORIGIN")) {
            variables.put("RENDER_EXTERNAL_URL", "https://valid-render.example");
        }
        variables.put(key, origin);
        var failure = assertThrows(Exception.class, () -> {
            try (var app = new SpringApplicationBuilder(CalendarApplication.class)
                    .environment(environment(variables)).run("--server.port=0")) { }
        });
        var messages = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        assertTrue(messages.toString().contains("calendar.public-origin"));
        assertFalse(messages.toString().contains("user:secret"));
    }

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
