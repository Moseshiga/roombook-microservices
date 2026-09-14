package dev.roombooking.notification.booking;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface ProcessedMessageRepository extends Repository<ProcessedMessage, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO processed_messages (event_id, event_type, processed_at)
            VALUES (:eventId, :eventType, CURRENT_TIMESTAMP)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int claim(@Param("eventId") UUID eventId, @Param("eventType") String eventType);

    @Query("""
            SELECT message.eventId
            FROM ProcessedMessage message
            WHERE message.processedAt < :cutoff
            ORDER BY message.processedAt, message.eventId
            """)
    List<UUID> findProcessedBefore(@Param("cutoff") Instant cutoff, Pageable pageable);

    @Modifying
    @Query("""
            DELETE FROM ProcessedMessage message
            WHERE message.eventId IN :eventIds
              AND message.processedAt < :cutoff
            """)
    int deleteProcessedBefore(
            @Param("eventIds") List<UUID> eventIds,
            @Param("cutoff") Instant cutoff);
}
