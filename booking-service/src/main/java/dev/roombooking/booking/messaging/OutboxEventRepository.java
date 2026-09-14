package dev.roombooking.booking.messaging;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface OutboxEventRepository extends Repository<OutboxEvent, UUID> {

    @Transactional(readOnly = true)
    @Query("""
            SELECT event
            FROM OutboxEvent event
            WHERE event.publishedAt IS NULL
              AND event.nextAttemptAt <= :now
            ORDER BY event.occurredAt, event.id
            """)
    List<OutboxEvent> findReadyToPublish(@Param("now") Instant now, Pageable pageable);

    @Modifying
    @Transactional
    @Query("""
            UPDATE OutboxEvent event
            SET event.publishedAt = :publishedAt,
                event.lastError = NULL
            WHERE event.id = :id
              AND event.publishedAt IS NULL
            """)
    int markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);

    @Modifying
    @Transactional
    @Query("""
            UPDATE OutboxEvent event
            SET event.attempts = event.attempts + 1,
                event.nextAttemptAt = :nextAttemptAt,
                event.lastError = :error
            WHERE event.id = :id
              AND event.publishedAt IS NULL
            """)
    int recordFailure(@Param("id") UUID id,
                      @Param("nextAttemptAt") Instant nextAttemptAt,
                      @Param("error") String error);

    @Query("""
            SELECT event.id
            FROM OutboxEvent event
            WHERE event.publishedAt IS NOT NULL
              AND event.publishedAt < :cutoff
            ORDER BY event.publishedAt, event.id
            """)
    List<UUID> findPublishedBefore(@Param("cutoff") Instant cutoff, Pageable pageable);

    @Modifying
    @Query("""
            DELETE FROM OutboxEvent event
            WHERE event.id IN :ids
              AND event.publishedAt IS NOT NULL
              AND event.publishedAt < :cutoff
            """)
    int deletePublishedBefore(@Param("ids") List<UUID> ids, @Param("cutoff") Instant cutoff);
}
