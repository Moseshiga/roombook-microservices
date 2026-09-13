package dev.roombooking.booking.reservation;

/**
 * Stores a booking event as part of the caller's transaction.
 */
public interface BookingConfirmedEventStore {
    void append(BookingConfirmedEvent event);
}
