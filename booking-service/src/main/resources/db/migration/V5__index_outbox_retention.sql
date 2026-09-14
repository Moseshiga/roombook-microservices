CREATE INDEX outbox_events_published_retention_idx
    ON outbox_events (published_at, id)
    WHERE published_at IS NOT NULL;
