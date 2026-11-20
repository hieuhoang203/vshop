-- S3 (docs/20): online payment through a provider (VNPay first). Additive. Tables follow the
-- reference design (intent, attempt, charge, allocation) plus the provider event log used to
-- de-duplicate and audit every callback. Money is integer VND.
CREATE SCHEMA payment;

-- Allocation ties an order to its own checkout group (composite key), as the reference design does.
ALTER TABLE sales.seller_order ADD CONSTRAINT uq_order_id_group UNIQUE (id, checkout_group_id);

CREATE TABLE payment.intent (
    id UUID PRIMARY KEY,
    checkout_group_id UUID NOT NULL REFERENCES sales.checkout_group(id),
    merchant_reference VARCHAR(100) NOT NULL UNIQUE,
    requested_amount BIGINT NOT NULL CHECK (requested_amount > 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    status VARCHAR(20) NOT NULL CHECK (status IN ('CREATED', 'PENDING', 'PAID', 'FAILED', 'UNKNOWN', 'CANCELED')),
    payment_deadline TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_intent_group UNIQUE (checkout_group_id),
    CONSTRAINT uq_intent_id_group UNIQUE (id, checkout_group_id)
);

CREATE TABLE payment.attempt (
    id UUID PRIMARY KEY,
    intent_id UUID NOT NULL REFERENCES payment.intent(id),
    checkout_group_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    merchant_attempt_reference VARCHAR(100) NOT NULL UNIQUE,
    provider_reference VARCHAR(150),
    status VARCHAR(20) NOT NULL CHECK (status IN ('CREATED', 'PENDING', 'SUCCEEDED', 'FAILED', 'EXPIRED', 'UNKNOWN')),
    client_ip VARCHAR(45) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_attempt_provider_ref UNIQUE (provider, provider_reference),
    CONSTRAINT uq_attempt_id_group UNIQUE (id, checkout_group_id),
    CONSTRAINT fk_attempt_intent_group FOREIGN KEY (intent_id, checkout_group_id)
        REFERENCES payment.intent(id, checkout_group_id)
);
CREATE INDEX ix_payment_attempt_intent ON payment.attempt(intent_id, created_at DESC);

CREATE TABLE payment.charge (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES payment.attempt(id),
    checkout_group_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_charge_reference VARCHAR(150) NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    captured_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_charge_provider_ref UNIQUE (provider, provider_charge_reference),
    CONSTRAINT uq_charge_id_group UNIQUE (id, checkout_group_id),
    CONSTRAINT fk_charge_attempt_group FOREIGN KEY (attempt_id, checkout_group_id)
        REFERENCES payment.attempt(id, checkout_group_id)
);
CREATE INDEX ix_payment_charge_attempt ON payment.charge(attempt_id);

CREATE TABLE payment.allocation (
    id UUID PRIMARY KEY,
    charge_id UUID NOT NULL REFERENCES payment.charge(id),
    seller_order_id UUID NOT NULL REFERENCES sales.seller_order(id),
    checkout_group_id UUID NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    CONSTRAINT uq_allocation_charge_order UNIQUE (charge_id, seller_order_id),
    CONSTRAINT uq_allocation_id_charge UNIQUE (id, charge_id),
    CONSTRAINT fk_allocation_charge_group FOREIGN KEY (charge_id, checkout_group_id)
        REFERENCES payment.charge(id, checkout_group_id),
    CONSTRAINT fk_allocation_order_group FOREIGN KEY (seller_order_id, checkout_group_id)
        REFERENCES sales.seller_order(id, checkout_group_id)
);
CREATE INDEX ix_payment_allocation_order ON payment.allocation(seller_order_id);

-- Every callback is logged once. The key is built from provider fields so a repeated delivery of the
-- same event is recognised; no card data is stored, only the verdict and the identifiers.
CREATE TABLE payment.provider_event (
    id UUID PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    event_key VARCHAR(250) NOT NULL,
    merchant_reference VARCHAR(100),
    outcome VARCHAR(30) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_provider_event UNIQUE (provider, event_key)
);

-- Orders paid for after they were canceled or expired need a person to decide (refund); this is the
-- queue. Nothing here moves money.
CREATE TABLE payment.review_case (
    id UUID PRIMARY KEY,
    charge_id UUID NOT NULL REFERENCES payment.charge(id),
    checkout_group_id UUID NOT NULL REFERENCES sales.checkout_group(id),
    reason VARCHAR(40) NOT NULL CHECK (reason IN ('PAID_AFTER_EXPIRY', 'AMOUNT_MISMATCH', 'DUPLICATE_CHARGE')),
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_review_charge_reason UNIQUE (charge_id, reason)
);
CREATE INDEX ix_review_group ON payment.review_case(checkout_group_id);
CREATE INDEX ix_review_open ON payment.review_case(created_at) WHERE status = 'OPEN';
