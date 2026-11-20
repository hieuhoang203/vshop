-- In-app notifications for buyers and shops, derived from outbox events. The unique (recipient,
-- dedupe key) pair makes at-least-once event delivery safe: a repeated event never adds a second
-- notification. Text carries order numbers and amounts, never contact details. Additive only.
CREATE TABLE platform.notification (
    id UUID PRIMARY KEY,
    recipient_account_id UUID NOT NULL REFERENCES identity.account(id),
    kind VARCHAR(40) NOT NULL,
    title VARCHAR(150) NOT NULL,
    body VARCHAR(500) NOT NULL,
    link VARCHAR(200),
    dedupe_key VARCHAR(200) NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notification_dedupe UNIQUE (recipient_account_id, dedupe_key)
);
CREATE INDEX ix_notification_recipient ON platform.notification(recipient_account_id, created_at DESC);
CREATE INDEX ix_notification_unread ON platform.notification(recipient_account_id) WHERE read_at IS NULL;
