package dev.roombooking.booking.reservation;

import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "booking.expiration.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
class ExpiredBookingCleanup {
    private static final Logger log = LoggerFactory.getLogger(ExpiredBookingCleanup.class);
    private final BookingService bookingService;

    ExpiredBookingCleanup(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelayString = "${booking.expiration.cleanup-interval:PT1M}")
    @SchedulerLock(name = "expire-overdue-bookings", lockAtMostFor = "PT5M")
    void expireOverdueBookings() {
        LockAssert.assertLocked();
        int expired = bookingService.expireOverduePending();
        if (expired > 0) {
            log.info("Expired {} pending booking(s)", expired);
        }
    }
}
