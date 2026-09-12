package dev.roombooking.booking.reservation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID roomId;
    @Column(nullable = false)
    private String userId;
    @Column(nullable = false)
    private Instant slotStart;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BookingStatus status;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant expiresAt;

    protected Booking() {
    }

    Booking(UUID roomId, String userId, Instant slotStart, Instant createdAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.roomId = roomId;
        this.userId = userId;
        this.slotStart = slotStart;
        this.status = BookingStatus.PENDING;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getRoomId() { return roomId; }
    public String getUserId() { return userId; }
    public Instant getSlotStart() { return slotStart; }
    public BookingStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
