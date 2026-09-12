package dev.roombooking.notification.messaging;

public final class NotificationMessagingTopology {
    public static final String EVENTS_EXCHANGE = "roombook.events";
    public static final String BOOKING_CONFIRMED_ROUTING_KEY = "booking.confirmed.v1";
    public static final String BOOKING_CONFIRMED_QUEUE = "notification.booking-confirmed.v1";
    public static final String DEAD_LETTER_EXCHANGE = "roombook.dead-letter";
    public static final String BOOKING_CONFIRMED_DEAD_LETTER_ROUTING_KEY =
            "notification.booking-confirmed.v1.failed";
    public static final String BOOKING_CONFIRMED_DEAD_LETTER_QUEUE =
            "notification.booking-confirmed.v1.dlq";

    private NotificationMessagingTopology() {
    }
}
