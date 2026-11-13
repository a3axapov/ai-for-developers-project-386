package com.testproject.calendar.availability;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock calendarClock() {
        return Clock.systemUTC();
    }
}
