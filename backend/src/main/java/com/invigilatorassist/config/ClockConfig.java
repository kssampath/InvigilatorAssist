package com.invigilatorassist.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Exam times are wall-clock times at the institution, so "now" is taken in its time zone. */
@Configuration
public class ClockConfig {
    @Bean
    Clock clock(@Value("${app.time-zone:Asia/Kolkata}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
