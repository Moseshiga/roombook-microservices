package dev.roombooking.booking.messaging;

import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

@Component
@ConditionalOnProperty(prefix = "booking.outbox.cleanup", name = "enabled",
        havingValue = "true", matchIfMissing = true)
class OutboxRetentionCleanup {
    private static final Logger log = LoggerFactory.getLogger(OutboxRetentionCleanup.class);

    private final OutboxRetentionService retentionService;
    private final Clock clock;
    private final Duration retention;
    private final int batchSize;

    OutboxRetentionCleanup(
            OutboxRetentionService retentionService,
            Clock clock,
            @Value("${booking.outbox.cleanup.retention:PT720H}") Duration retention,
            @Value("${booking.outbox.cleanup.batch-size:500}") int batchSize) {
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("Outbox retention must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Outbox cleanup batch size must be positive");
        }
        this.retentionService = retentionService;
        this.clock = clock;
        this.retention = retention;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${booking.outbox.cleanup.interval:PT1H}")
    @SchedulerLock(name = "cleanup-published-booking-outbox", lockAtMostFor = "PT30M")
    void deleteExpiredBatch() {
        LockAssert.assertLocked();
        int deleted = retentionService.deletePublishedBefore(clock.instant().minus(retention), batchSize);
        if (deleted > 0) {
            log.info("Deleted {} published outbox events past retention", deleted);
        }
    }
}
