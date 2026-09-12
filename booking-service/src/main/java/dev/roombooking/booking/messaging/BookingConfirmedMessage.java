package dev.roombooking.booking.messaging;

import dev.roombooking.booking.reservation.BookingConfirmedEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Version 1 of the public integration contract sent to RabbitMQ.
 */
public record BookingConfirmedMessage(
        UUID eventId,
        Instant occurredAt,
        UUID bookingId,
        UUID roomId,
        String userId,
        Instant slotStart,
        Instant slotEnd
) {
    static BookingConfirmedMessage from(BookingConfirmedEvent event) {
        return new BookingConfirmedMessage(
                event.eventId(),
                event.occurredAt(),
                event.bookingId(),
                event.roomId(),
                event.userId(),
                event.slotStart(),
                event.slotEnd()
        );
    }
}
