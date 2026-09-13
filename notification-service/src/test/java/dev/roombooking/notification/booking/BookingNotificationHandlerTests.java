package dev.roombooking.notification.booking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.dynamic=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@Testcontainers
class BookingNotificationHandlerTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(384L * 1024 * 1024));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired BookingNotificationHandler handler;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean NotificationSender notificationSender;

    @BeforeEach
    void clearInbox() {
        jdbc.update("DELETE FROM processed_messages");
    }

    @Test
    void handlesTheSameEventOnlyOnce() {
        BookingConfirmedMessage message = message();

        handler.handle(message);
        handler.handle(message);

        verify(notificationSender).sendBookingConfirmation(message);
        verifyNoMoreInteractions(notificationSender);
        assertThat(processedMessageCount(message.eventId())).isOne();
    }

    @Test
    void rollsBackTheClaimWhenNotificationHandlingFails() {
        BookingConfirmedMessage message = message();
        doThrow(new IllegalStateException("delivery failed"))
                .doNothing()
                .when(notificationSender).sendBookingConfirmation(message);

        assertThatThrownBy(() -> handler.handle(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("delivery failed");
        assertThat(processedMessageCount(message.eventId())).isZero();

        handler.handle(message);

        verify(notificationSender, times(2)).sendBookingConfirmation(message);
        assertThat(processedMessageCount(message.eventId())).isOne();
    }

    @Test
    void concurrentConsumersStillHandleTheEventOnlyOnce() throws Exception {
        BookingConfirmedMessage message = message();
        CountDownLatch firstHandlerEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstHandler = new CountDownLatch(1);
        doAnswer(invocation -> {
            firstHandlerEntered.countDown();
            assertThat(releaseFirstHandler.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(notificationSender).sendBookingConfirmation(message);

        try (var consumers = Executors.newFixedThreadPool(2)) {
            var first = consumers.submit(() -> handler.handle(message));
            assertThat(firstHandlerEntered.await(5, TimeUnit.SECONDS)).isTrue();
            var duplicate = consumers.submit(() -> handler.handle(message));

            releaseFirstHandler.countDown();
            first.get(10, TimeUnit.SECONDS);
            duplicate.get(10, TimeUnit.SECONDS);
        } finally {
            releaseFirstHandler.countDown();
        }

        verify(notificationSender).sendBookingConfirmation(message);
        assertThat(processedMessageCount(message.eventId())).isOne();
    }

    private int processedMessageCount(UUID eventId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM processed_messages WHERE event_id = ?", Integer.class, eventId);
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
