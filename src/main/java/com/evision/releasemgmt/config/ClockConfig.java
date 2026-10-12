package com.evision.releasemgmt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    // D-07-10 bounds compare against now; injecting the clock is what makes them testable.
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
