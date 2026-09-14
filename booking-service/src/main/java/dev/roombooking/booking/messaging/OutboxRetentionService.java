package dev.roombooking.booking.messaging;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OutboxRetentionService {
    private final OutboxEventRepository repository;

    OutboxRetentionService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int deletePublishedBefore(Instant cutoff, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        var ids = repository.findPublishedBefore(cutoff, PageRequest.of(0, batchSize));
        return ids.isEmpty() ? 0 : repository.deletePublishedBefore(ids, cutoff);
    }
}
