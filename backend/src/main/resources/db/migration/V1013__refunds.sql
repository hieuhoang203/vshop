-- R0 (docs/24): refunds. A refund returns money collected online for one order. It is created in the
-- same transaction as the event that causes it (cancel, reject, late payment), waits for the buyer's
-- bank account, and is paid out by a person who records the bank reference. Additive only.
-- The reference design also had return_case_id; it is added together with return cases (R1).
CREATE TABLE payment.refund (
    id UUID PRIMARY KEY,
    charge_id UUID NOT NULL REFERENCES payment.charge(id),
    checkout_group_id UUID NOT NULL,
    seller_order_id UUID NOT NULL,
    reason VARCHAR(30) NOT NULL CHECK (reason IN ('ORDER_CANCELED', 'SELLER_REJECTED', 'PAID_AFTER_EXPIRY', 'ADMIN_GOODWILL')),
    idempotency_key VARCHAR(150) NOT NULL UNIQUE,
    provider_refund_reference VARCHAR(150),
    amount BIGINT NOT NULL CHECK (amount > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('AWAITING_ACCOUNT', 'APPROVED', 'SUBMITTED', 'PENDING', 'SUCCEEDED', 'FAILED', 'UNKNOWN')),
    bank_name VARCHAR(100),
    account_number_ciphertext TEXT,
    account_name_ciphertext TEXT,
    payout_reference VARCHAR(100),
    note VARCHAR(500),
    paid_by_subject VARCHAR(255),
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_refund_charge_provider_ref UNIQUE (charge_id, provider_refund_reference),
    CONSTRAINT uq_refund_id_charge UNIQUE (id, charge_id),
    CONSTRAINT fk_refund_order_group FOREIGN KEY (seller_order_id, checkout_group_id)
        REFERENCES sales.seller_order(id, checkout_group_id),
    CONSTRAINT fk_refund_charge_group FOREIGN KEY (charge_id, checkout_group_id)
        REFERENCES payment.charge(id, checkout_group_id),
    -- A payout can only be requested once the destination is known, and a finished refund is paid.
    CONSTRAINT ck_refund_destination CHECK (
        status = 'AWAITING_ACCOUNT'
        OR status = 'FAILED'
        OR (bank_name IS NOT NULL AND account_number_ciphertext IS NOT NULL AND account_name_ciphertext IS NOT NULL)
    ),
    CONSTRAINT ck_refund_paid CHECK (
        status <> 'SUCCEEDED' OR (payout_reference IS NOT NULL AND paid_at IS NOT NULL AND paid_by_subject IS NOT NULL)
    )
);
CREATE INDEX ix_refund_order ON payment.refund(seller_order_id);
CREATE INDEX ix_refund_open ON payment.refund(created_at) WHERE status IN ('AWAITING_ACCOUNT', 'APPROVED');

CREATE TABLE payment.refund_allocation (
    refund_id UUID NOT NULL,
    charge_id UUID NOT NULL,
    allocation_id UUID NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    PRIMARY KEY (refund_id, allocation_id),
    CONSTRAINT fk_refund_alloc_refund FOREIGN KEY (refund_id, charge_id)
        REFERENCES payment.refund(id, charge_id),
    CONSTRAINT fk_refund_alloc_allocation FOREIGN KEY (allocation_id, charge_id)
        REFERENCES payment.allocation(id, charge_id)
);
CREATE INDEX ix_refund_allocation_charge ON payment.refund_allocation(allocation_id, charge_id);
