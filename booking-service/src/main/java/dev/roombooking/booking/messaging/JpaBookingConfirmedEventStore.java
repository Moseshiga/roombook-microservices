package dev.roombooking.booking.messaging;

import dev.roombooking.booking.reservation.BookingConfirmedEvent;
import dev.roombooking.booking.reservation.BookingConfirmedEventStore;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
class JpaBookingConfirmedEventStore implements BookingConfirmedEventStore {
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

    JpaBookingConfirmedEventStore(EntityManager entityManager, ObjectMapper objectMapper) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(BookingConfirmedEvent event) {
        BookingConfirmedMessage message = BookingConfirmedMessage.from(event);
        entityManager.persist(OutboxEvent.bookingConfirmed(event, objectMapper.writeValueAsString(message)));
    }
}
