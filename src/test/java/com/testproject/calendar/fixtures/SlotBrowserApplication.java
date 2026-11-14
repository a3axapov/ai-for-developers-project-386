package com.testproject.calendar.fixtures;

import com.testproject.calendar.CalendarApplication;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import org.springframework.boot.builder.SpringApplicationBuilder;

// Loaded only from test classes by PropertiesLauncher, never packaged in the production application.
public final class SlotBrowserApplication {
    private SlotBrowserApplication() { }

    public static void main(String[] args) {
        var now = Arrays.stream(args).filter(arg -> arg.startsWith("--slot-test.now="))
                .map(arg -> arg.substring("--slot-test.now=".length())).findFirst().orElseThrow();
        var clockFile = Arrays.stream(args).filter(arg -> arg.startsWith("--slot-test.clock-file="))
                .map(arg -> Path.of(arg.substring("--slot-test.clock-file=".length()))).findFirst();
        Clock clock = clockFile.<Clock>map(path -> new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return Clock.fixed(instant(), zone);
            }

            @Override
            public Instant instant() {
                try {
                    return Instant.parse(Files.readString(path).trim());
                } catch (java.io.IOException failure) {
                    throw new IllegalStateException("Cannot read test clock", failure);
                }
            }
        }).orElseGet(() -> Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
        new SpringApplicationBuilder(CalendarApplication.class).initializers(context -> context.getBeanFactory()
                .registerSingleton("browserTestClock", clock)).run(args);
    }
}
