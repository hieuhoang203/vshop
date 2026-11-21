-- R3 (docs/24): automatic payout of a refund through the payment provider. The refund keeps the
-- states it already had; SUBMITTED now means "the provider has the transfer request" and is
-- resolved by the provider's answer or by a status check. Additive only.
ALTER TABLE payment.refund
    ADD COLUMN bank_bin VARCHAR(10),
    ADD COLUMN payout_provider VARCHAR(20),
    ADD COLUMN payout_provider_ref VARCHAR(150),
    ADD COLUMN payout_submitted_at TIMESTAMPTZ,
    ADD COLUMN payout_failure VARCHAR(500),
    ADD CONSTRAINT ck_refund_bin CHECK (bank_bin IS NULL OR bank_bin ~ '^[0-9]{6}$'),
    ADD CONSTRAINT ck_refund_submitted CHECK (
        status <> 'SUBMITTED' OR (payout_provider IS NOT NULL AND payout_provider_ref IS NOT NULL AND payout_submitted_at IS NOT NULL)
    );
CREATE INDEX ix_refund_submitted ON payment.refund(payout_submitted_at) WHERE status = 'SUBMITTED';
