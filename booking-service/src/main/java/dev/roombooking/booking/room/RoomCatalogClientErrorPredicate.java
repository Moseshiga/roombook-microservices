package dev.roombooking.booking.room;

import feign.FeignException;

import java.util.function.Predicate;

/**
 * Excludes client-side HTTP outcomes from availability statistics. A request
 * rejected with 4xx says nothing about whether room-service is operational.
 */
public final class RoomCatalogClientErrorPredicate implements Predicate<Throwable> {
    @Override
    public boolean test(Throwable failure) {
        return failure instanceof FeignException feignException
                && feignException.status() >= 400
                && feignException.status() < 500;
    }
}
