package dev.roombooking.booking.reservation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record BookingResponse(UUID id, UUID roomId, UUID userId, Instant slotStart,
                              Instant slotEnd, BookingStatus status, Instant createdAt,
                              Instant expiresAt) {
    static BookingResponse from(Booking booking, Instant now) {
        // Reads expose effective expiry without requiring a scheduler or writing on GET.
        BookingStatus effectiveStatus = booking.getStatus() == BookingStatus.PENDING
                && !booking.getExpiresAt().isAfter(now) ? BookingStatus.EXPIRED : booking.getStatus();
        return new BookingResponse(booking.getId(), booking.getRoomId(), booking.getUserId(),
                booking.getSlotStart(), booking.getSlotStart().plus(1, ChronoUnit.HOURS),
                effectiveStatus, booking.getCreatedAt(), booking.getExpiresAt());
    }
}
