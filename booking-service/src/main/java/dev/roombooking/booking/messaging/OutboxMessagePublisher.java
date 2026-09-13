package dev.roombooking.booking.messaging;

import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "booking.outbox.publisher", name = "enabled",
        havingValue = "true", matchIfMissing = true)
class OutboxMessagePublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxMessagePublisher.class);
    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);
    private static final int BATCH_SIZE = 50;
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(5);

    private final OutboxEventRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    OutboxMessagePublisher(OutboxEventRepository repository, RabbitTemplate rabbitTemplate,
                           ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${booking.outbox.publisher.interval:PT5S}")
    @SchedulerLock(name = "publish-booking-outbox", lockAtMostFor = "PT10M")
    void publishPendingEvents() {
        LockAssert.assertLocked();
        publishReadyBatch();
    }

    int publishReadyBatch() {
        Instant now = clock.instant();
        var events = repository.findReadyToPublish(now, PageRequest.of(0, BATCH_SIZE));
        for (OutboxEvent event : events) {
            publish(event);
        }
        return events.size();
    }

    private void publish(OutboxEvent event) {
        try {
            BookingConfirmedMessage message = deserialize(event);
            CorrelationData correlation = new CorrelationData(event.id().toString());
            rabbitTemplate.convertAndSend(
                    BookingMessagingTopology.EVENTS_EXCHANGE,
                    event.eventType(),
                    message,
                    rabbitMessage -> {
                        rabbitMessage.getMessageProperties().setMessageId(event.id().toString());
                        rabbitMessage.getMessageProperties().setType(event.eventType());
                        rabbitMessage.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return rabbitMessage;
                    },
                    correlation);

            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack()) {
                throw new IllegalStateException("RabbitMQ rejected the message: " + confirm.reason());
            }
            if (correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ could not route the message: "
                        + correlation.getReturned().getReplyText());
            }
            repository.markPublished(event.id(), clock.instant());
        } catch (Exception exception) {
            Instant nextAttemptAt = clock.instant().plus(retryDelay(event.attempts() + 1));
            repository.recordFailure(event.id(), nextAttemptAt, conciseMessage(exception));
            log.warn("Could not publish outbox event {}; next attempt at {}",
                    event.id(), nextAttemptAt, exception);
        }
    }

    private BookingConfirmedMessage deserialize(OutboxEvent event) {
        if (!BookingMessagingTopology.BOOKING_CONFIRMED_ROUTING_KEY.equals(event.eventType())) {
            throw new IllegalArgumentException("Unsupported outbox event type: " + event.eventType());
        }
        return objectMapper.readValue(event.payload(), BookingConfirmedMessage.class);
    }

    private Duration retryDelay(int attempt) {
        long seconds = 1L << Math.min(attempt, 8);
        return Duration.ofSeconds(Math.min(seconds, MAX_RETRY_DELAY.toSeconds()));
    }

    private String conciseMessage(Exception exception) {
        String message = exception.getMessage();
        String result = exception.getClass().getSimpleName() + (message == null ? "" : ": " + message);
        return result.length() <= 1000 ? result : result.substring(0, 1000);
    }
}
