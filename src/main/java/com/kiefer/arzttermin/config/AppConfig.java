package com.kiefer.arzttermin.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application-wide infrastructure configuration.
 *
 * <p>Business code must use the injected clock instead of calling
 * {@code Instant.now()} or {@code LocalDateTime.now()} directly. This keeps
 * offer deadlines and the relative demo dates deterministic in tests.</p>
 */
@Configuration
@EnableScheduling
public class AppConfig {

    public static final ZoneId BUSINESS_TIME_ZONE = ZoneId.of("Europe/Berlin");

    @Bean
    Clock applicationClock() {
        return Clock.system(BUSINESS_TIME_ZONE);
    }
}
