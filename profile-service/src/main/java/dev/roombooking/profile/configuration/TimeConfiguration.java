package dev.roombooking.profile.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class TimeConfiguration {
    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
