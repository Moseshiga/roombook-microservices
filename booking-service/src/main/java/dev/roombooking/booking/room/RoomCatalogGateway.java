package dev.roombooking.booking.room;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class RoomCatalogGateway {
    private final RoomCatalogClient roomCatalogClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public RoomCatalogGateway(RoomCatalogClient roomCatalogClient,
                              CircuitBreakerRegistry circuitBreakerRegistry,
                              RetryRegistry retryRegistry) {
        this.roomCatalogClient = roomCatalogClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("roomCatalog");
        this.retry = retryRegistry.retry("roomCatalog");
    }

    public void requireActiveRoom(UUID roomId) {
        try {
            // The circuit breaker observes one logical lookup. The retry runs inside it,
            // so several network attempts count as one success or failure in its window.
            RoomCatalogResponse room = circuitBreaker.executeSupplier(
                    () -> retry.executeSupplier(() -> roomCatalogClient.getActiveRoom(roomId)));
            if (!roomId.equals(room.id()) || !room.active()) {
                throw roomUnavailable(roomId);
            }
        } catch (FeignException.NotFound exception) {
            throw roomUnavailable(roomId);
        } catch (CallNotPermittedException exception) {
            throw catalogUnavailable(exception);
        } catch (FeignException exception) {
            throw catalogUnavailable(exception);
        }
    }

    private ResponseStatusException catalogUnavailable(RuntimeException cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Room catalog is temporarily unavailable", cause);
    }

    private ResponseStatusException roomUnavailable(UUID roomId) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Room %s does not exist or is inactive".formatted(roomId));
    }
}
