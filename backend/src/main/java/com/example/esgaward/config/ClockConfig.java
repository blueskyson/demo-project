package com.example.esgaward.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Injected wherever "now" matters (e.g. award event deadlines) so tests can control time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
