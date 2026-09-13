package dev.roombooking.notification.booking;

import dev.roombooking.notification.messaging.NotificationMessagingTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingNotificationHandler {
    private static final Logger log = LoggerFactory.getLogger(BookingNotificationHandler.class);

    private final ProcessedMessageRepository processedMessages;
    private final NotificationSender notificationSender;

    public BookingNotificationHandler(
            ProcessedMessageRepository processedMessages,
            NotificationSender notificationSender) {
        this.processedMessages = processedMessages;
        this.notificationSender = notificationSender;
    }

    @Transactional
    public void handle(BookingConfirmedMessage message) {
        int claimed = processedMessages.claim(
                message.eventId(), NotificationMessagingTopology.BOOKING_CONFIRMED_ROUTING_KEY);
        if (claimed == 0) {
            log.info("Skipping already processed event: eventId={}, eventType={}",
                    message.eventId(), NotificationMessagingTopology.BOOKING_CONFIRMED_ROUTING_KEY);
            return;
        }

        notificationSender.sendBookingConfirmation(message);
    }
}
