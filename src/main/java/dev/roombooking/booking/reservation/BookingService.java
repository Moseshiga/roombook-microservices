package dev.roombooking.booking.reservation;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class BookingService {
    private static final Duration HOLD_DURATION = Duration.ofMinutes(10);
    private final BookingRepository repository;
    private final Clock clock;

    public BookingService(BookingRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public BookingResponse create(CreateBookingRequest request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (!request.slotStart().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slotStart must be in the future");
        }
        if (!request.slotStart().equals(request.slotStart().truncatedTo(ChronoUnit.HOURS))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slotStart must be on a whole UTC hour");
        }
        Instant expiresAt = now.plus(HOLD_DURATION);
        if (expiresAt.isAfter(request.slotStart())) {
            expiresAt = request.slotStart();
        }
        repository.expirePendingSlot(request.roomId(), request.slotStart(), now);
        Booking booking = new Booking(request.roomId(), request.userId(), request.slotStart(), now, expiresAt);
        // The unique index decides the winner even when requests reach different instances.
        // Flush here so a conflict leaves this transaction and is handled after rollback.
        repository.saveAndFlush(booking);
        return BookingResponse.from(booking, now);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID id) {
        Booking booking = requireBooking(id);
        return BookingResponse.from(booking, clock.instant());
    }

    @Transactional
    public BookingResponse confirm(UUID id) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (repository.confirmPending(id, now) == 1) {
            return BookingResponse.from(requireBooking(id), now);
        }
        throw transitionRejected(id, "confirmed", now);
    }

    @Transactional
    public BookingResponse cancel(UUID id) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (repository.cancelPending(id, now) == 1) {
            return BookingResponse.from(requireBooking(id), now);
        }
        throw transitionRejected(id, "cancelled", now);
    }

    @Transactional
    public int expireOverduePending() {
        return repository.expireOverduePending(clock.instant().truncatedTo(ChronoUnit.MICROS));
    }

    private Booking requireBooking(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    private ResponseStatusException transitionRejected(UUID id, String targetState, Instant now) {
        Booking booking = requireBooking(id);
        if (booking.getStatus() == BookingStatus.PENDING && !booking.getExpiresAt().isAfter(now)) {
            return new ResponseStatusException(HttpStatus.CONFLICT, "Booking hold has expired");
        }
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Booking in state " + booking.getStatus() + " cannot be " + targetState);
    }
}
