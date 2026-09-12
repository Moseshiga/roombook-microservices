package dev.roombooking.booking.messaging;

import dev.roombooking.booking.reservation.BookingConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BookingConfirmedMessagePublisher {
    private static final Logger log = LoggerFactory.getLogger(BookingConfirmedMessagePublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public BookingConfirmedMessagePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(BookingConfirmedEvent event) {
        BookingConfirmedMessage message = BookingConfirmedMessage.from(event);
        try {
            rabbitTemplate.convertAndSend(
                    BookingMessagingTopology.EVENTS_EXCHANGE,
                    BookingMessagingTopology.BOOKING_CONFIRMED_ROUTING_KEY,
                    message,
                    rabbitMessage -> {
                        rabbitMessage.getMessageProperties().setMessageId(event.eventId().toString());
                        rabbitMessage.getMessageProperties().setType(
                                BookingMessagingTopology.BOOKING_CONFIRMED_ROUTING_KEY);
                        rabbitMessage.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return rabbitMessage;
                    }
            );
        } catch (AmqpException exception) {
            // The database has already committed. The outbox stage will make this hand-off reliable.
            log.error("Could not publish booking confirmation event {} for booking {}",
                    event.eventId(), event.bookingId(), exception);
        }
    }
}
