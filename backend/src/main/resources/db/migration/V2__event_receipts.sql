CREATE TABLE platform_event_receipt (
    event_id UUID PRIMARY KEY REFERENCES outbox_event(id),
    event_type VARCHAR(100) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
