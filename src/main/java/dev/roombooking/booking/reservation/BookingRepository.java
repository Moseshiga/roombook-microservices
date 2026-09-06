package dev.roombooking.booking.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    @Modifying
    @Query(value = """
            UPDATE bookings SET status = 'EXPIRED'
            WHERE room_id = :roomId AND slot_start = :slotStart
              AND status = 'PENDING' AND expires_at <= :now
            """, nativeQuery = true)
    int expirePendingSlot(@Param("roomId") UUID roomId, @Param("slotStart") Instant slotStart,
                          @Param("now") Instant now);
}
