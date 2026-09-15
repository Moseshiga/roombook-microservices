ALTER TABLE outbox_events
    ADD COLUMN trace_id VARCHAR(32),
    ADD COLUMN trace_span_id VARCHAR(16),
    ADD COLUMN trace_sampled BOOLEAN;

COMMENT ON COLUMN outbox_events.trace_id IS
    'Trace that created the event; retained so the delayed outbox publisher can continue it.';
COMMENT ON COLUMN outbox_events.trace_span_id IS
    'Span that created the event and becomes the parent of the later publish span.';
