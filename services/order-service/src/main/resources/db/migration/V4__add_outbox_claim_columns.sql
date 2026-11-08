ALTER TABLE outbox_events
    ADD COLUMN claimed_by UUID,
    ADD COLUMN claimed_until TIMESTAMPTZ;

CREATE INDEX idx_outbox_events_claimable
    ON outbox_events (occurred_at, id)
    WHERE published_at IS NULL;
