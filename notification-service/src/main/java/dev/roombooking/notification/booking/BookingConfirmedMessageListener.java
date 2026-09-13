package dev.roombooking.notification.booking;

import dev.roombooking.notification.messaging.NotificationMessagingTopology;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BookingConfirmedMessageListener {
    private final BookingNotificationHandler notificationHandler;

    public BookingConfirmedMessageListener(BookingNotificationHandler notificationHandler) {
        this.notificationHandler = notificationHandler;
    }

    @RabbitListener(queues = NotificationMessagingTopology.BOOKING_CONFIRMED_QUEUE)
    public void handle(BookingConfirmedMessage message) {
        notificationHandler.handle(message);
    }
}
