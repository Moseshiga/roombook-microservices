package dev.roombooking.booking.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxMessagePublisherTests {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final OutboxMessagePublisher publisher =
            new OutboxMessagePublisher(repository, rabbitTemplate, objectMapper, clock);

    @Test
    void marksEventPublishedOnlyAfterRabbitConfirmsIt() {
        OutboxEvent event = event();
        when(repository.findReadyToPublish(eq(NOW), any(Pageable.class))).thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(BookingMessagingTopology.EVENTS_EXCHANGE),
                eq(BookingMessagingTopology.BOOKING_CONFIRMED_ROUTING_KEY),
                any(BookingConfirmedMessage.class),
                any(MessagePostProcessor.class),
                any(CorrelationData.class));

        assertThat(publisher.publishReadyBatch()).isEqualTo(1);

        verify(repository).markPublished(event.id(), NOW);
        verify(repository, never()).recordFailure(eq(event.id()), any(), any());
    }

    @Test
    void schedulesRetryWhenRabbitRejectsTheMessage() {
        OutboxEvent event = event();
        when(repository.findReadyToPublish(eq(NOW), any(Pageable.class))).thenReturn(List.of(event));
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "broker error"));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                any(String.class), any(String.class), any(),
                any(MessagePostProcessor.class), any(CorrelationData.class));

        publisher.publishReadyBatch();

        verify(repository, never()).markPublished(any(), any());
        verify(repository).recordFailure(eq(event.id()), eq(NOW.plusSeconds(2)),
                any(String.class));
    }

    private OutboxEvent event() {
        UUID eventId = UUID.randomUUID();
        String payload = """
                {
                  "eventId":"%s",
                  "occurredAt":"2030-01-01T10:00:00Z",
                  "bookingId":"%s",
                  "roomId":"%s",
                  "userId":"user-123",
                  "slotStart":"2030-01-02T12:00:00Z",
                  "slotEnd":"2030-01-02T13:00:00Z"
                }
                """.formatted(eventId, UUID.randomUUID(), UUID.randomUUID());
        var domainEvent = new dev.roombooking.booking.reservation.BookingConfirmedEvent(
                eventId,
                NOW,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "user-123",
                Instant.parse("2030-01-02T12:00:00Z"),
                Instant.parse("2030-01-02T13:00:00Z"));
        return OutboxEvent.bookingConfirmed(domainEvent, payload);
    }
}
