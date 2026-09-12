package dev.roombooking.booking.messaging;

public final class BookingMessagingTopology {
    public static final String EVENTS_EXCHANGE = "roombook.events";
    public static final String BOOKING_CONFIRMED_ROUTING_KEY = "booking.confirmed.v1";

    private BookingMessagingTopology() {
    }
}
