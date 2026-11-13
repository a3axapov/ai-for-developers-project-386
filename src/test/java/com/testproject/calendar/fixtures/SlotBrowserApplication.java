package com.testproject.calendar.fixtures;

import com.testproject.calendar.CalendarApplication;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.springframework.boot.builder.SpringApplicationBuilder;

// Loaded only from test classes by PropertiesLauncher, never packaged in the production application.
public final class SlotBrowserApplication {
    private SlotBrowserApplication() { }

    public static void main(String[] args) {
        var now = Arrays.stream(args).filter(arg -> arg.startsWith("--slot-test.now="))
                .map(arg -> arg.substring("--slot-test.now=".length())).findFirst().orElseThrow();
        new SpringApplicationBuilder(CalendarApplication.class).initializers(context -> context.getBeanFactory()
                .registerSingleton("browserTestClock", Clock.fixed(Instant.parse(now), ZoneOffset.UTC))).run(args);
    }
}
