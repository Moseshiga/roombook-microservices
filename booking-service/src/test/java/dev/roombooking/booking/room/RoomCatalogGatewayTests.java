package dev.roombooking.booking.room;

import feign.FeignException;
import feign.Request;
import feign.Response;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RoomCatalogGatewayTests {
    private static final UUID ROOM_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private RoomCatalogClient client;

    private RoomCatalogGateway gateway;
    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        var transientFailure = new TransientRoomCatalogFailurePredicate();
        var retryConfig = RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ZERO)
                .retryOnException(transientFailure)
                .build();
        var circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .recordException(transientFailure)
                .build();

        RetryRegistry retryRegistry = RetryRegistry.ofDefaults();
        retryRegistry.retry("roomCatalog", retryConfig);
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("roomCatalog", circuitBreakerConfig);
        gateway = new RoomCatalogGateway(client, circuitBreakerRegistry, retryRegistry);
    }

    @Test
    void retriesATransientServerFailureOnce() {
        given(client.getActiveRoom(ROOM_ID)).willThrow(feignFailure(503));

        assertUnavailable(() -> gateway.requireActiveRoom(ROOM_ID));

        verify(client, times(2)).getActiveRoom(ROOM_ID);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isOne();
    }

    @Test
    void doesNotRetryOrRecordANotFoundResponseAsAServiceFailure() {
        given(client.getActiveRoom(ROOM_ID)).willThrow(feignFailure(404));

        assertThatThrownBy(() -> gateway.requireActiveRoom(ROOM_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(client).getActiveRoom(ROOM_ID);
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void failsFastAfterTheCircuitOpens() {
        given(client.getActiveRoom(ROOM_ID)).willThrow(feignFailure(503));

        assertUnavailable(() -> gateway.requireActiveRoom(ROOM_ID));
        assertUnavailable(() -> gateway.requireActiveRoom(ROOM_ID));
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertUnavailable(() -> gateway.requireActiveRoom(ROOM_ID));
        verify(client, times(4)).getActiveRoom(ROOM_ID);
    }

    private void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private FeignException feignFailure(int status) {
        return FeignException.errorStatus(
                "RoomCatalogClient#getActiveRoom",
                Response.builder()
                        .status(status)
                        .reason(HttpStatus.valueOf(status).getReasonPhrase())
                        .request(Request.create(
                                Request.HttpMethod.GET,
                                "http://room-service/api/rooms/" + ROOM_ID,
                                Map.of(),
                                null,
                                StandardCharsets.UTF_8,
                                null))
                        .build());
    }
}
