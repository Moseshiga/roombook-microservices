package dev.roombooking.notification.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingNotificationSender implements NotificationSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void sendBookingConfirmation(BookingConfirmedMessage message) {
        // A real adapter can later replace this with email, SMS or push delivery.
        log.info("Booking confirmation notification: eventId={}, bookingId={}, userId={}, slot={}..{}",
                message.eventId(), message.bookingId(), message.userId(),
                message.slotStart(), message.slotEnd());
    }
}
