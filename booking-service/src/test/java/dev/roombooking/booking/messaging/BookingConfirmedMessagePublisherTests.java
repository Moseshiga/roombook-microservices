package dev.roombooking.booking.messaging;

import dev.roombooking.booking.reservation.BookingConfirmedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.event.TransactionalEventListenerFactory;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BookingConfirmedMessagePublisherTests {
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void defersRabbitPublicationUntilTheTransactionCommits() {
        try (var context = publisherContext()) {
            beginTransaction();

            context.publishEvent(event());

            verifyNoMessageWasSent();
            completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
            verifyMessageWasSent();
        }
    }

    @Test
    void skipsRabbitPublicationWhenTheTransactionRollsBack() {
        try (var context = publisherContext()) {
            beginTransaction();

            context.publishEvent(event());
            completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

            verifyNoMessageWasSent();
        }
    }

    @Test
    void skipsPublicationWhenThereIsNoTransaction() {
        try (var context = publisherContext()) {
            context.publishEvent(event());

            verifyNoMessageWasSent();
        }
    }

    private AnnotationConfigApplicationContext publisherContext() {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(TransactionalEventListenerFactory.class);
        context.registerBean(RabbitTemplate.class, () -> rabbitTemplate);
        context.registerBean(BookingConfirmedMessagePublisher.class);
        context.refresh();
        return context;
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private void completeTransaction(int status) {
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
        synchronizations.forEach(TransactionSynchronization::beforeCompletion);
        if (status == TransactionSynchronization.STATUS_COMMITTED) {
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        }
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(status));
    }

    private BookingConfirmedEvent event() {
        return new BookingConfirmedEvent(
                UUID.randomUUID(),
                Instant.parse("2030-01-01T10:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "keycloak-subject",
                Instant.parse("2030-01-02T12:00:00Z"),
                Instant.parse("2030-01-02T13:00:00Z")
        );
    }

    private void verifyMessageWasSent() {
        verify(rabbitTemplate).convertAndSend(
                eq(BookingMessagingTopology.EVENTS_EXCHANGE),
                eq(BookingMessagingTopology.BOOKING_CONFIRMED_ROUTING_KEY),
                any(BookingConfirmedMessage.class),
                any(MessagePostProcessor.class));
    }

    private void verifyNoMessageWasSent() {
        verify(rabbitTemplate, never()).convertAndSend(
                eq(BookingMessagingTopology.EVENTS_EXCHANGE),
                eq(BookingMessagingTopology.BOOKING_CONFIRMED_ROUTING_KEY),
                any(BookingConfirmedMessage.class),
                any(MessagePostProcessor.class));
    }
}
