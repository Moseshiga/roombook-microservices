package dev.roombooking.booking.reservation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "booking_requests")
class BookingRequest {
    @Id
    private UUID id;
    @Column(nullable = false, length = 255)
    private String userId;
    @Column(nullable = false, length = 128)
    private String idempotencyKey;
    @Column(nullable = false)
    private UUID roomId;
    @Column(nullable = false)
    private Instant slotStart;
    private UUID bookingId;
    @Column(nullable = false)
    private Instant createdAt;

    protected BookingRequest() {
    }

    UUID getRoomId() {
        return roomId;
    }

    Instant getSlotStart() {
        return slotStart;
    }

    UUID getBookingId() {
        return bookingId;
    }
}
