CREATE TABLE notifications (
                               id UUID PRIMARY KEY,
                               event_id UUID NOT NULL UNIQUE,
                               order_id UUID NOT NULL,
                               message VARCHAR(500) NOT NULL,
                               created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT fk_notifications_processed_event
                                   FOREIGN KEY (event_id)
                                       REFERENCES processed_events(event_id)
);

CREATE INDEX idx_notifications_order_id
    ON notifications (order_id);
