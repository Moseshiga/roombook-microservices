package dev.roombooking.booking.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@Validated
public class BookingController {
    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request,
                                                   @RequestHeader("Idempotency-Key")
                                                   @NotBlank @Size(max = 128)
                                                   @Pattern(regexp = "[\\x21-\\x7E]+",
                                                           message = "must contain visible ASCII characters only")
                                                   String idempotencyKey,
                                                   JwtAuthenticationToken authentication) {
        BookingCreationResult result = service.create(idempotencyKey, request, BookingActor.from(authentication));
        BookingResponse booking = result.booking();
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .location(URI.create("/api/bookings/" + booking.id()))
                .body(booking);
    }

    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable UUID id, JwtAuthenticationToken authentication) {
        return service.get(id, BookingActor.from(authentication));
    }

    @PostMapping("/{id}/confirm")
    public BookingResponse confirm(@PathVariable UUID id, JwtAuthenticationToken authentication) {
        return service.confirm(id, BookingActor.from(authentication));
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable UUID id, JwtAuthenticationToken authentication) {
        return service.cancel(id, BookingActor.from(authentication));
    }
}
