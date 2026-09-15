package dev.roombooking.booking.messaging;

import dev.roombooking.booking.reservation.BookingConfirmedEvent;
import io.micrometer.tracing.TraceContext;
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
    @Column(length = 32)
    private String traceId;
    @Column(length = 16)
    private String traceSpanId;
    private Boolean traceSampled;

    protected OutboxEvent() {
    }

    private OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String eventType,
                        String payload, Instant occurredAt, TraceContext traceContext) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.attempts = 0;
        this.nextAttemptAt = occurredAt;
        if (traceContext != null) {
            this.traceId = traceContext.traceId();
            this.traceSpanId = traceContext.spanId();
            this.traceSampled = traceContext.sampled();
        }
    }

    static OutboxEvent bookingConfirmed(BookingConfirmedEvent event, String payload,
                                        TraceContext traceContext) {
        return new OutboxEvent(
                event.eventId(),
                "BOOKING",
                event.bookingId(),
                BookingMessagingTopology.BOOKING_CONFIRMED_ROUTING_KEY,
                payload,
                event.occurredAt(),
                traceContext);
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

    String traceId() {
        return traceId;
    }

    String traceSpanId() {
        return traceSpanId;
    }

    Boolean traceSampled() {
        return traceSampled;
    }
}
