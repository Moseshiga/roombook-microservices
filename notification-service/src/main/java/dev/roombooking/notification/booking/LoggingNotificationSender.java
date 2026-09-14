package dev.roombooking.notification.booking;

import dev.roombooking.notification.profile.NotificationProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingNotificationSender implements NotificationSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void sendBookingConfirmation(BookingConfirmedMessage message, NotificationProfile profile) {
        // A real adapter can later replace this with email, SMS or push delivery.
        // Do not log the recipient email: it is personally identifiable information.
        log.info("Booking confirmation notification: eventId={}, bookingId={}, userId={}, locale={}, slot={}..{}",
                message.eventId(), message.bookingId(), message.userId(), profile.locale(),
                message.slotStart(), message.slotEnd());
    }
}
