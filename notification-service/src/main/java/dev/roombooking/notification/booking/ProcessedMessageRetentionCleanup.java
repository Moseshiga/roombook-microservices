package dev.roombooking.notification.booking;

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
@ConditionalOnProperty(prefix = "notification.inbox.cleanup", name = "enabled",
        havingValue = "true", matchIfMissing = true)
class ProcessedMessageRetentionCleanup {
    private static final Logger log = LoggerFactory.getLogger(ProcessedMessageRetentionCleanup.class);

    private final ProcessedMessageRetentionService retentionService;
    private final Clock clock;
    private final Duration retention;
    private final int batchSize;

    ProcessedMessageRetentionCleanup(
            ProcessedMessageRetentionService retentionService,
            Clock clock,
            @Value("${notification.inbox.cleanup.retention:PT2160H}") Duration retention,
            @Value("${notification.inbox.cleanup.batch-size:500}") int batchSize) {
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("Inbox retention must be positive");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Inbox cleanup batch size must be positive");
        }
        this.retentionService = retentionService;
        this.clock = clock;
        this.retention = retention;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${notification.inbox.cleanup.interval:PT1H}")
    @SchedulerLock(name = "cleanup-processed-notification-messages", lockAtMostFor = "PT30M")
    void deleteExpiredBatch() {
        LockAssert.assertLocked();
        int deleted = retentionService.deleteProcessedBefore(clock.instant().minus(retention), batchSize);
        if (deleted > 0) {
            log.info("Deleted {} processed messages past inbox retention", deleted);
        }
    }
}
