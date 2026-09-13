package dev.roombooking.booking.messaging;

import dev.roombooking.booking.reservation.BookingConfirmedEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
class OutboxEvent {
    @Id
    private UUID id;
    @Column(nullable = false, length = 64)
    private String aggregateType;
    @Column(nullable = false)
    private UUID aggregateId;
    @Column(nullable = false, length = 128)
    private String eventType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;
    @Column(nullable = false)
    private Instant occurredAt;
    private Instant publishedAt;
    @Column(nullable = false)
    private int attempts;
    @Column(nullable = false)
    private Instant nextAttemptAt;
    private String lastError;

    protected OutboxEvent() {
    }

    private OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String eventType,
                        String payload, Instant occurredAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.attempts = 0;
        this.nextAttemptAt = occurredAt;
    }

    static OutboxEvent bookingConfirmed(BookingConfirmedEvent event, String payload) {
        return new OutboxEvent(
                event.eventId(),
                "BOOKING",
                event.bookingId(),
                BookingMessagingTopology.BOOKING_CONFIRMED_ROUTING_KEY,
                payload,
                event.occurredAt());
    }

    UUID id() {
        return id;
    }

    String eventType() {
        return eventType;
    }

    String payload() {
        return payload;
    }

    int attempts() {
        return attempts;
    }
}
