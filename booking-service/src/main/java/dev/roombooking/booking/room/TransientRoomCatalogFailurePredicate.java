package dev.roombooking.booking.room;

import feign.FeignException;
import feign.RetryableException;

import java.util.function.Predicate;

/**
 * Classifies only failures that can reasonably disappear on a later attempt.
 * Client errors such as 404 describe the request and must neither be retried nor
 * make the circuit breaker conclude that room-service is unhealthy.
 */
public final class TransientRoomCatalogFailurePredicate implements Predicate<Throwable> {
    @Override
    public boolean test(Throwable failure) {
        if (failure instanceof RetryableException) {
            return true;
        }
        return failure instanceof FeignException feignException && feignException.status() >= 500;
    }
}
