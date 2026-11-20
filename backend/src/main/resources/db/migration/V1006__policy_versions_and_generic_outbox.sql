-- S0 of the checkout design (docs/20). Additive: a new table and one defaulted outbox column, so no
-- data is moved or removed and no backup is required.
--
-- Business parameters (shipping fee, return policy, COD limits, payment window) are versioned rows.
-- A row is a DRAFT until an ADMIN approves it; only the current APPROVED row of each type is ever
-- used, so a number typed in by a developer cannot silently become a rule.
CREATE TABLE platform.policy_version (
    id UUID PRIMARY KEY,
    policy_type VARCHAR(50) NOT NULL,
    version INTEGER NOT NULL,
    content JSONB NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'DRAFT',
    effective_from TIMESTAMPTZ,
    effective_to TIMESTAMPTZ,
    created_by_account_id UUID NOT NULL REFERENCES identity.account(id),
    approved_by_account_id UUID REFERENCES identity.account(id),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_policy_type_version UNIQUE (policy_type, version),
    CONSTRAINT ck_policy_type CHECK (policy_type IN ('SHIPPING_FLAT', 'RETURN', 'COD_LIMITS', 'PAYMENT_WINDOW')),
    CONSTRAINT ck_policy_version CHECK (version > 0),
    CONSTRAINT ck_policy_content CHECK (jsonb_typeof(content) = 'object'),
    CONSTRAINT ck_policy_status CHECK (status IN ('DRAFT', 'APPROVED', 'RETIRED')),
    CONSTRAINT ck_policy_effective CHECK (effective_to IS NULL OR (effective_from IS NOT NULL AND effective_to > effective_from)),
    CONSTRAINT ck_policy_approved CHECK (
        (status = 'DRAFT' AND effective_from IS NULL AND approved_by_account_id IS NULL AND approved_at IS NULL)
        OR (status <> 'DRAFT' AND effective_from IS NOT NULL AND approved_by_account_id IS NOT NULL AND approved_at IS NOT NULL)
    )
);
-- At most one APPROVED (current) policy per type.
CREATE UNIQUE INDEX uq_policy_current ON platform.policy_version(policy_type) WHERE status = 'APPROVED';
CREATE INDEX ix_policy_created_by ON platform.policy_version(created_by_account_id);
CREATE INDEX ix_policy_approved_by ON platform.policy_version(approved_by_account_id);

-- Outbox events were audit-derived and carried only a shop id. They now name the aggregate they are
-- about, so order and payment events can use the same table.
ALTER TABLE platform.outbox_event ADD COLUMN aggregate_type VARCHAR(50) NOT NULL DEFAULT 'audit';
COMMENT ON TABLE platform.policy_version IS 'Versioned business parameters. Content is immutable once approved (enforced by PolicyService); a new version replaces it.';
