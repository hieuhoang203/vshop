-- R1 (docs/24): return cases after delivery. The buyer opens a case for a delivered order within the
-- return window; the shop answers, or an administrator decides; an approved case creates a refund
-- through the R0 ledger. Return shipping is manual in this slice (the buyer types a tracking code).
-- Additive: new schema and tables, a widened policy type check, and a nullable charge on refunds so
-- cash-on-delivery orders (no online charge) can be refunded too.
CREATE SCHEMA aftersales;

ALTER TABLE platform.policy_version DROP CONSTRAINT ck_policy_type;
ALTER TABLE platform.policy_version ADD CONSTRAINT ck_policy_type CHECK (
    policy_type IN ('SHIPPING_FLAT', 'RETURN', 'COD_LIMITS', 'PAYMENT_WINDOW', 'SELLER_CONFIRMATION', 'RETURN_HANDLING')
);

CREATE TABLE aftersales.return_case (
    id UUID PRIMARY KEY,
    seller_order_id UUID NOT NULL,
    shop_id UUID NOT NULL,
    buyer_account_id UUID NOT NULL REFERENCES identity.account(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('REQUESTED', 'SELLER_OFFERED', 'SELLER_REJECTED', 'AWAITING_RETURN', 'IN_RETURN', 'REVIEWING', 'REFUND_APPROVED', 'REJECTED', 'CANCELED')),
    reason_code VARCHAR(30) NOT NULL CHECK (reason_code IN ('NOT_RECEIVED', 'MISSING_ITEMS', 'WRONG_ITEM', 'DAMAGED', 'DEFECTIVE', 'NOT_AS_DESCRIBED', 'FAKE', 'CHANGED_MIND')),
    preferred_resolution VARCHAR(20) NOT NULL CHECK (preferred_resolution IN ('REFUND_ONLY', 'RETURN_AND_REFUND')),
    resolution VARCHAR(20) CHECK (resolution IN ('REFUND_ONLY', 'RETURN_AND_REFUND')),
    description VARCHAR(1000) NOT NULL,
    requested_amount BIGINT NOT NULL CHECK (requested_amount > 0),
    offered_amount BIGINT CHECK (offered_amount > 0),
    approved_amount BIGINT CHECK (approved_amount > 0),
    policy_id UUID NOT NULL REFERENCES platform.policy_version(id),
    seller_deadline_at TIMESTAMPTZ NOT NULL,
    seller_note VARCHAR(500),
    admin_note VARCHAR(500),
    return_tracking_code VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_case_id_order UNIQUE (id, seller_order_id),
    CONSTRAINT fk_case_order_shop FOREIGN KEY (seller_order_id, shop_id)
        REFERENCES sales.seller_order(id, shop_id),
    CONSTRAINT ck_case_offer CHECK (offered_amount IS NULL OR offered_amount <= requested_amount),
    CONSTRAINT ck_case_approved CHECK (approved_amount IS NULL OR approved_amount <= requested_amount),
    CONSTRAINT ck_case_refund_approved CHECK (status <> 'REFUND_APPROVED' OR (approved_amount IS NOT NULL AND resolution IS NOT NULL)),
    -- CHANGED_MIND needs the goods back; refund-only is for faults.
    CONSTRAINT ck_case_changed_mind CHECK (reason_code <> 'CHANGED_MIND' OR preferred_resolution = 'RETURN_AND_REFUND'),
    CONSTRAINT ck_case_shipped CHECK (status NOT IN ('IN_RETURN') OR return_tracking_code IS NOT NULL)
);
-- One case per order unless the buyer canceled it.
CREATE UNIQUE INDEX uq_case_live_per_order ON aftersales.return_case(seller_order_id) WHERE status <> 'CANCELED';
CREATE INDEX ix_case_buyer ON aftersales.return_case(buyer_account_id, created_at DESC);
CREATE INDEX ix_case_shop_status ON aftersales.return_case(shop_id, status, created_at DESC);
CREATE INDEX ix_case_policy ON aftersales.return_case(policy_id);
CREATE INDEX ix_case_attention ON aftersales.return_case(seller_deadline_at) WHERE status IN ('REQUESTED', 'REVIEWING');

CREATE TABLE aftersales.return_case_event (
    id UUID PRIMARY KEY,
    return_case_id UUID NOT NULL REFERENCES aftersales.return_case(id),
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    actor_type VARCHAR(10) NOT NULL CHECK (actor_type IN ('BUYER', 'SELLER', 'ADMIN', 'SYSTEM')),
    actor_subject VARCHAR(255),
    note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_case_event ON aftersales.return_case_event(return_case_id, created_at, id);

-- Refunds may now come from a return case, and a cash-on-delivery refund has no online charge.
ALTER TABLE payment.refund ALTER COLUMN charge_id DROP NOT NULL;
ALTER TABLE payment.refund
    ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'ONLINE' CHECK (source IN ('ONLINE', 'COD')),
    ADD COLUMN return_case_id UUID REFERENCES aftersales.return_case(id),
    ADD CONSTRAINT ck_refund_source CHECK ((source = 'ONLINE') = (charge_id IS NOT NULL));
ALTER TABLE payment.refund DROP CONSTRAINT refund_reason_check;
ALTER TABLE payment.refund ADD CONSTRAINT refund_reason_check CHECK (
    reason IN ('ORDER_CANCELED', 'SELLER_REJECTED', 'PAID_AFTER_EXPIRY', 'ADMIN_GOODWILL', 'RETURN_APPROVED')
);
CREATE INDEX ix_refund_case ON payment.refund(return_case_id);
