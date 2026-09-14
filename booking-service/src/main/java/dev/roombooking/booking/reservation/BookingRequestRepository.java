package dev.roombooking.booking.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface BookingRequestRepository extends JpaRepository<BookingRequest, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO booking_requests (
                id, user_id, idempotency_key, room_id, slot_start, created_at
            ) VALUES (
                :id, :userId, :idempotencyKey, :roomId, :slotStart, :createdAt
            )
            ON CONFLICT (user_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("id") UUID id,
              @Param("userId") String userId,
              @Param("idempotencyKey") String idempotencyKey,
              @Param("roomId") UUID roomId,
              @Param("slotStart") Instant slotStart,
              @Param("createdAt") Instant createdAt);

    Optional<BookingRequest> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    @Modifying
    @Query("""
            UPDATE BookingRequest request
            SET request.bookingId = :bookingId
            WHERE request.id = :requestId AND request.bookingId IS NULL
            """)
    int complete(@Param("requestId") UUID requestId, @Param("bookingId") UUID bookingId);
}
