package dev.roombooking.booking.messaging;

import dev.roombooking.booking.reservation.BookingConfirmedEvent;
import dev.roombooking.booking.reservation.BookingConfirmedEventStore;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

@Repository
class JpaBookingConfirmedEventStore implements BookingConfirmedEventStore {
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final Optional<Tracer> tracer;

    JpaBookingConfirmedEventStore(EntityManager entityManager, ObjectMapper objectMapper,
                                  Optional<Tracer> tracer) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(BookingConfirmedEvent event) {
        BookingConfirmedMessage message = BookingConfirmedMessage.from(event);
        entityManager.persist(OutboxEvent.bookingConfirmed(
                event,
                objectMapper.writeValueAsString(message),
                currentTraceContext()));
    }

    private TraceContext currentTraceContext() {
        return tracer.map(Tracer::currentSpan).map(Span::context).orElse(null);
    }
}
