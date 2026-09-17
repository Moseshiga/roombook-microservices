package dev.roombooking.booking.reservation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Bookings", description = "One-hour room holds owned by the authenticated user")
public class BookingController {
    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "Create a booking hold",
            description = "Creates a temporary one-hour hold. Repeating the same request with the same "
                    + "Idempotency-Key returns the original result.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "A new hold was created"),
            @ApiResponse(responseCode = "200", description = "An idempotent replay returned the existing hold"),
            @ApiResponse(responseCode = "400", description = "The request or idempotency key is invalid"),
            @ApiResponse(responseCode = "401", description = "The access token is missing or invalid"),
            @ApiResponse(responseCode = "403", description = "The caller lacks a required realm role"),
            @ApiResponse(responseCode = "409", description = "The slot is unavailable or the key was reused for another request"),
            @ApiResponse(responseCode = "503", description = "The room catalog is temporarily unavailable")
    })
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody CreateBookingRequest request,
                                                   @Parameter(
                                                           description = "Client-generated key reused for safe retries of this request",
                                                           example = "f3ff45b4-10d8-4c18-9730-d11ad78a29d6")
                                                   @RequestHeader("Idempotency-Key")
                                                   @NotBlank @Size(max = 128)
                                                   @Pattern(regexp = "[\\x21-\\x7E]+",
                                                           message = "must contain visible ASCII characters only")
                                                   String idempotencyKey,
                                                   @Parameter(hidden = true)
                                                   JwtAuthenticationToken authentication) {
        BookingCreationResult result = service.create(idempotencyKey, request, BookingActor.from(authentication));
        BookingResponse booking = result.booking();
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .location(URI.create("/api/bookings/" + booking.id()))
                .body(booking);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a booking", description = "Returns a booking visible to its owner or an administrator.")
    public BookingResponse get(@PathVariable UUID id,
                               @Parameter(hidden = true) JwtAuthenticationToken authentication) {
        return service.get(id, BookingActor.from(authentication));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm a booking", description = "Confirms a non-expired PENDING hold.")
    public BookingResponse confirm(@PathVariable UUID id,
                                   @Parameter(hidden = true) JwtAuthenticationToken authentication) {
        return service.confirm(id, BookingActor.from(authentication));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a booking", description = "Cancels a non-expired PENDING hold.")
    public BookingResponse cancel(@PathVariable UUID id,
                                  @Parameter(hidden = true) JwtAuthenticationToken authentication) {
        return service.cancel(id, BookingActor.from(authentication));
    }
}
