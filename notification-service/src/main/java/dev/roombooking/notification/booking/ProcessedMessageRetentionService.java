package dev.roombooking.notification.booking;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ProcessedMessageRetentionService {
    private final ProcessedMessageRepository repository;

    ProcessedMessageRetentionService(ProcessedMessageRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int deleteProcessedBefore(Instant cutoff, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        var eventIds = repository.findProcessedBefore(cutoff, PageRequest.of(0, batchSize));
        return eventIds.isEmpty() ? 0 : repository.deleteProcessedBefore(eventIds, cutoff);
    }
}
