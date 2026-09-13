package dev.roombooking.notification.booking;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BookingConfirmedMessageListenerTests {

    @Test
    void delegatesTheDeserializedContractToTheTransactionalHandler() {
        BookingNotificationHandler handler = mock(BookingNotificationHandler.class);
        BookingConfirmedMessageListener listener = new BookingConfirmedMessageListener(handler);
        BookingConfirmedMessage message = message();

        listener.handle(message);

        verify(handler).handle(message);
    }

    @Test
    void deserializesJsonIntoTheConsumersOwnContractType() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        BookingConfirmedMessage original = message();
        var rabbitMessage = converter.toMessage(original, new MessageProperties());
        rabbitMessage.getMessageProperties().setHeader(
                "__TypeId__", "dev.roombooking.booking.messaging.BookingConfirmedMessage");
        rabbitMessage.getMessageProperties().setInferredArgumentType(BookingConfirmedMessage.class);

        Object converted = converter.fromMessage(rabbitMessage);

        assertThat(converted).isEqualTo(original);
    }

    private BookingConfirmedMessage message() {
        return new BookingConfirmedMessage(
                UUID.randomUUID(),
                Instant.parse("2030-01-01T10:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "keycloak-subject",
                Instant.parse("2030-01-02T12:00:00Z"),
                Instant.parse("2030-01-02T13:00:00Z")
        );
    }
}
