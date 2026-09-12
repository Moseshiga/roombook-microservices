package dev.roombooking.notification.booking;

import java.time.Instant;
import java.util.UUID;

/**
 * The consumer's own representation of the booking.confirmed.v1 contract.
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
}
