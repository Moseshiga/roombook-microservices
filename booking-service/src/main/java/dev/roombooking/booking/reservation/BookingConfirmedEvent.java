package dev.roombooking.booking.reservation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * An in-process domain event. Publishing it does not itself perform network I/O.
 */
public record BookingConfirmedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID bookingId,
        UUID roomId,
        String userId,
        Instant slotStart,
        Instant slotEnd
) {
    static BookingConfirmedEvent from(Booking booking, Instant occurredAt) {
        return new BookingConfirmedEvent(
                UUID.randomUUID(),
                occurredAt,
                booking.getId(),
                booking.getRoomId(),
                booking.getUserId(),
                booking.getSlotStart(),
                booking.getSlotStart().plus(1, ChronoUnit.HOURS)
        );
    }
}
