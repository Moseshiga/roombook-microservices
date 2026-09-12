package dev.roombooking.booking.reservation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
class TimeConfiguration {
    @Bean
    Clock bookingClock() {
        return Clock.systemUTC();
    }
}
