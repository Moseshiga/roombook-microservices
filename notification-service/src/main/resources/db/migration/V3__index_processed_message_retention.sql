CREATE INDEX processed_messages_retention_idx
    ON processed_messages (processed_at, event_id);
