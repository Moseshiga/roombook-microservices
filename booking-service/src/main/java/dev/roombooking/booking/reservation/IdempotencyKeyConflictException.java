package dev.roombooking.booking.reservation;

final class IdempotencyKeyConflictException extends RuntimeException {
    IdempotencyKeyConflictException() {
        super("The Idempotency-Key has already been used with a different booking request");
    }
}
