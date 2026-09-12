package dev.roombooking.booking.reservation;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull UUID roomId,
        @NotNull Instant slotStart
) {
}
