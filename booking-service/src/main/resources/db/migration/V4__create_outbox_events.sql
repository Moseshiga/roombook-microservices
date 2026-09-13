CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error TEXT,
    CONSTRAINT outbox_attempts_check CHECK (attempts >= 0)
);

CREATE INDEX outbox_events_pending_idx
    ON outbox_events (next_attempt_at, occurred_at)
    WHERE published_at IS NULL;
