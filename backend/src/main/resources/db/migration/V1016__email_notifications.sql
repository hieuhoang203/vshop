-- Email as a second channel for notifications. Opt-in only: a person turns it on in settings and the
-- address comes from their verified sign-in identity, never from user-typed text. The address is
-- encrypted. A delivery row is created next to each notification of an opted-in person; the address
-- is read again at send time, so turning email off stops mail that is still queued. Additive only.
CREATE TABLE identity.notification_email (
    account_id UUID PRIMARY KEY REFERENCES identity.account(id),
    email_ciphertext TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    verified_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE platform.email_delivery (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL REFERENCES platform.notification(id),
    account_id UUID NOT NULL REFERENCES identity.account(id),
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'SKIPPED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    locked_until TIMESTAMPTZ,
    last_error VARCHAR(300),
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_email_delivery_notification UNIQUE (notification_id),
    CONSTRAINT ck_email_sent CHECK (status <> 'SENT' OR sent_at IS NOT NULL)
);
CREATE INDEX ix_email_delivery_due ON platform.email_delivery(next_attempt_at) WHERE status IN ('PENDING', 'SENDING');
CREATE INDEX ix_email_delivery_account ON platform.email_delivery(account_id);
