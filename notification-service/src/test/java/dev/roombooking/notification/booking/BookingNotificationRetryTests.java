package dev.roombooking.notification.booking;

import dev.roombooking.notification.messaging.NotificationMessagingTopology;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.retry.max-retries=2",
        "spring.rabbitmq.listener.simple.retry.initial-interval=10ms",
        "spring.rabbitmq.listener.simple.retry.multiplier=1",
        "spring.rabbitmq.listener.simple.retry.max-interval=10ms",
        "notification.inbox.cleanup.enabled=false"
})
@Testcontainers
class BookingNotificationRetryTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(384L * 1024 * 1024));

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.2-management-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(384L * 1024 * 1024));

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }

    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired AmqpAdmin rabbitAdmin;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean NotificationSender notificationSender;

    @BeforeEach
    void clearState() {
        jdbc.update("DELETE FROM processed_messages");
        rabbitAdmin.purgeQueue(NotificationMessagingTopology.BOOKING_CONFIRMED_QUEUE);
        rabbitAdmin.purgeQueue(NotificationMessagingTopology.BOOKING_CONFIRMED_DEAD_LETTER_QUEUE);
    }

    @Test
    void retriesTransientFailureAndAcknowledgesTheSuccessfulAttempt() throws Exception {
        BookingConfirmedMessage message = message();
        doThrow(new IllegalStateException("provider temporarily unavailable"))
                .doThrow(new IllegalStateException("provider temporarily unavailable"))
                .doNothing()
                .when(notificationSender).sendBookingConfirmation(message);

        publish(message);

        await(() -> processedMessageCount(message.eventId()) == 1);
        verify(notificationSender, times(3)).sendBookingConfirmation(message);
        assertThat(rabbitAdmin.getQueueInfo(NotificationMessagingTopology.BOOKING_CONFIRMED_QUEUE)
                .getMessageCount()).isZero();
        assertThat(rabbitAdmin.getQueueInfo(NotificationMessagingTopology.BOOKING_CONFIRMED_DEAD_LETTER_QUEUE)
                .getMessageCount()).isZero();
    }

    @Test
    void deadLettersMessageAfterRetriesAreExhausted() {
        BookingConfirmedMessage message = message();
        doThrow(new IllegalStateException("provider unavailable"))
                .when(notificationSender).sendBookingConfirmation(message);

        publish(message);

        Message deadLetter = rabbitTemplate.receive(
                NotificationMessagingTopology.BOOKING_CONFIRMED_DEAD_LETTER_QUEUE, 5_000);
        assertThat(deadLetter).isNotNull();
        verify(notificationSender, times(3)).sendBookingConfirmation(message);
        assertThat(processedMessageCount(message.eventId())).isZero();
    }

    private void publish(BookingConfirmedMessage message) {
        rabbitTemplate.convertAndSend(
                NotificationMessagingTopology.EVENTS_EXCHANGE,
                NotificationMessagingTopology.BOOKING_CONFIRMED_ROUTING_KEY,
                message);
    }

    private int processedMessageCount(UUID eventId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM processed_messages WHERE event_id = ?", Integer.class, eventId);
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertThat(condition.getAsBoolean()).isTrue();
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
