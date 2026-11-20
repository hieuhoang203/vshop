-- REFERENCE DESIGN, NOT AN APPLICATION MIGRATION.
-- Apply only to the isolated design-validation database after core Flyway DDL.
-- The associated APIs, state machines and money-handling services are not implemented.
BEGIN;

CREATE SCHEMA sales;
CREATE SCHEMA promotion;
CREATE SCHEMA payment;
CREATE SCHEMA fulfillment;
CREATE SCHEMA aftersales;
CREATE SCHEMA finance;
CREATE SCHEMA engagement;
CREATE SCHEMA media;

CREATE TABLE platform.policy_version (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_type VARCHAR(50) NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    content JSONB NOT NULL CHECK (jsonb_typeof(content) = 'object'),
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (policy_type, version),
    CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE TABLE identity.address (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES identity.account(id),
    recipient_name VARCHAR(150) NOT NULL,
    phone_ciphertext TEXT NOT NULL,
    address_ciphertext TEXT NOT NULL,
    administrative_code VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_address_account ON identity.address(account_id);

CREATE TABLE merchant.bank_account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id UUID NOT NULL REFERENCES merchant.shop(id),
    bank_code VARCHAR(30) NOT NULL,
    beneficiary_ciphertext TEXT NOT NULL,
    account_ciphertext TEXT NOT NULL,
    masked_account VARCHAR(40) NOT NULL,
    verification_status VARCHAR(20) NOT NULL CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED', 'REVOKED')),
    verified_at TIMESTAMPTZ,
    hold_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (id, shop_id),
    CHECK (verification_status <> 'VERIFIED' OR verified_at IS NOT NULL)
);
CREATE INDEX ix_bank_account_shop ON merchant.bank_account(shop_id);

CREATE TABLE media.asset (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_account_id UUID NOT NULL REFERENCES identity.account(id),
    shop_id UUID REFERENCES merchant.shop(id),
    object_key VARCHAR(500) NOT NULL UNIQUE,
    mime_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL CHECK (byte_size > 0),
    checksum VARCHAR(128) NOT NULL,
    visibility VARCHAR(20) NOT NULL CHECK (visibility IN ('PRIVATE', 'PUBLIC')),
    scan_status VARCHAR(20) NOT NULL CHECK (scan_status IN ('QUARANTINED', 'SAFE', 'BLOCKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (visibility <> 'PUBLIC' OR scan_status = 'SAFE')
);
CREATE INDEX ix_asset_owner ON media.asset(owner_account_id);
CREATE INDEX ix_asset_shop ON media.asset(shop_id);

CREATE TABLE catalog.product_media (
    product_id UUID NOT NULL REFERENCES catalog.product(id),
    asset_id UUID NOT NULL REFERENCES media.asset(id),
    sort_order INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id, asset_id)
);
CREATE INDEX ix_product_media_asset ON catalog.product_media(asset_id);

-- checkout.cart, checkout.cart_line and checkout.quote are implemented by Flyway V1004.
-- Reference-only addition: the price policy a quote was calculated under (nullable until the
-- policy module exists, because existing rows have none).
ALTER TABLE checkout.quote ADD COLUMN price_policy_id UUID REFERENCES platform.policy_version(id);
CREATE INDEX ix_quote_policy ON checkout.quote(price_policy_id);

CREATE TABLE sales.checkout_group (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    buyer_account_id UUID NOT NULL REFERENCES identity.account(id),
    quote_id UUID NOT NULL UNIQUE REFERENCES checkout.quote(id),
    idempotency_key VARCHAR(150) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    total_amount BIGINT NOT NULL CHECK (total_amount >= 0),
    payment_method VARCHAR(20) NOT NULL CHECK (payment_method IN ('COD', 'PSP')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (buyer_account_id, idempotency_key)
);
CREATE TABLE sales.seller_order (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checkout_group_id UUID NOT NULL REFERENCES sales.checkout_group(id),
    shop_id UUID NOT NULL REFERENCES merchant.shop(id),
    order_number VARCHAR(40) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING_PAYMENT', 'AWAITING_SELLER', 'CONFIRMED', 'PACKING', 'READY_TO_SHIP', 'IN_FULFILLMENT', 'DELIVERED', 'COMPLETED', 'CANCEL_REQUESTED', 'CANCELED', 'REJECTED')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    item_amount BIGINT NOT NULL CHECK (item_amount >= 0),
    discount_amount BIGINT NOT NULL CHECK (discount_amount >= 0),
    shipping_amount BIGINT NOT NULL CHECK (shipping_amount >= 0),
    payable_amount BIGINT NOT NULL CHECK (payable_amount >= 0),
    recipient_snapshot_ciphertext TEXT NOT NULL,
    return_policy_id UUID NOT NULL REFERENCES platform.policy_version(id),
    delivered_at TIMESTAMPTZ,
    return_eligible_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (checkout_group_id, shop_id),
    UNIQUE (id, checkout_group_id),
    UNIQUE (id, shop_id),
    CHECK (discount_amount <= item_amount),
    CHECK (payable_amount = item_amount - discount_amount + shipping_amount)
);
CREATE INDEX ix_order_shop_created ON sales.seller_order(shop_id, created_at DESC);
CREATE INDEX ix_order_policy ON sales.seller_order(return_policy_id);
CREATE TABLE sales.order_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_order_id UUID NOT NULL,
    shop_id UUID NOT NULL,
    sku_id UUID NOT NULL,
    product_name_snapshot VARCHAR(200) NOT NULL,
    variant_snapshot JSONB NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price BIGINT NOT NULL CHECK (unit_price > 0),
    gross_amount BIGINT NOT NULL CHECK (gross_amount > 0),
    discount_amount BIGINT NOT NULL CHECK (discount_amount >= 0),
    net_amount BIGINT NOT NULL CHECK (net_amount >= 0),
    UNIQUE (id, seller_order_id),
    FOREIGN KEY (seller_order_id, shop_id) REFERENCES sales.seller_order(id, shop_id),
    FOREIGN KEY (sku_id, shop_id) REFERENCES catalog.product_sku(id, shop_id),
    CHECK (gross_amount = unit_price * quantity),
    CHECK (net_amount = gross_amount - discount_amount)
);
CREATE INDEX ix_order_line_order_shop ON sales.order_line(seller_order_id, shop_id);
CREATE INDEX ix_order_line_sku_shop ON sales.order_line(sku_id, shop_id);
CREATE TABLE sales.order_adjustment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_order_id UUID NOT NULL REFERENCES sales.seller_order(id),
    order_line_id UUID,
    adjustment_type VARCHAR(30) NOT NULL CHECK (adjustment_type IN ('SELLER_DISCOUNT', 'PLATFORM_DISCOUNT', 'SHIP_SUBSIDY', 'FEE', 'TAX')),
    funding_party VARCHAR(20) NOT NULL CHECK (funding_party IN ('BUYER', 'SELLER', 'PLATFORM', 'CARRIER', 'PARTNER')),
    amount BIGINT NOT NULL CHECK (amount >= 0),
    basis_snapshot JSONB NOT NULL,
    policy_id UUID NOT NULL REFERENCES platform.policy_version(id),
    FOREIGN KEY (order_line_id, seller_order_id) REFERENCES sales.order_line(id, seller_order_id)
);
CREATE INDEX ix_adjustment_order ON sales.order_adjustment(seller_order_id);
CREATE INDEX ix_adjustment_line_order ON sales.order_adjustment(order_line_id, seller_order_id);
CREATE INDEX ix_adjustment_policy ON sales.order_adjustment(policy_id);

CREATE TABLE promotion.voucher (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) NOT NULL UNIQUE,
    scope VARCHAR(20) NOT NULL CHECK (scope IN ('SHOP', 'PLATFORM', 'SHIPPING')),
    shop_id UUID REFERENCES merchant.shop(id),
    funding_party VARCHAR(20) NOT NULL CHECK (funding_party IN ('SELLER', 'PLATFORM', 'PARTNER')),
    discount_type VARCHAR(20) NOT NULL CHECK (discount_type IN ('FIXED', 'PERCENT')),
    discount_value NUMERIC(18, 4) NOT NULL CHECK (discount_value > 0),
    max_discount BIGINT CHECK (max_discount > 0),
    min_spend BIGINT NOT NULL DEFAULT 0 CHECK (min_spend >= 0),
    budget_amount BIGINT NOT NULL CHECK (budget_amount >= 0),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    policy_id UUID NOT NULL REFERENCES platform.policy_version(id),
    CHECK ((scope = 'SHOP' AND shop_id IS NOT NULL) OR (scope <> 'SHOP' AND shop_id IS NULL)),
    CHECK (ends_at > starts_at),
    CHECK (discount_type <> 'PERCENT' OR discount_value <= 100)
);
CREATE INDEX ix_voucher_shop ON promotion.voucher(shop_id);
CREATE INDEX ix_voucher_policy ON promotion.voucher(policy_id);
CREATE TABLE promotion.redemption (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    voucher_id UUID NOT NULL REFERENCES promotion.voucher(id),
    checkout_group_id UUID NOT NULL REFERENCES sales.checkout_group(id),
    allocated_amount BIGINT NOT NULL CHECK (allocated_amount >= 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('HELD', 'CONSUMED', 'RELEASED')),
    expires_at TIMESTAMPTZ NOT NULL,
    UNIQUE (voucher_id, checkout_group_id)
);
CREATE INDEX ix_redemption_checkout ON promotion.redemption(checkout_group_id);

CREATE TABLE fulfillment.shipment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_order_id UUID NOT NULL,
    shop_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    direction VARCHAR(10) NOT NULL CHECK (direction IN ('FORWARD', 'RETURN')),
    carrier_code VARCHAR(30) NOT NULL,
    tracking_number VARCHAR(100),
    status VARCHAR(30) NOT NULL CHECK (status IN ('CREATED', 'READY', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'DELIVERY_FAILED', 'RETURNING', 'RETURNED', 'LOST', 'CANCELED')),
    cod_amount BIGINT NOT NULL DEFAULT 0 CHECK (cod_amount >= 0),
    recipient_snapshot_ciphertext TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (carrier_code, tracking_number),
    UNIQUE (id, seller_order_id),
    FOREIGN KEY (seller_order_id, shop_id) REFERENCES sales.seller_order(id, shop_id),
    FOREIGN KEY (warehouse_id, shop_id) REFERENCES inventory.warehouse(id, shop_id)
);
CREATE INDEX ix_shipment_order_shop ON fulfillment.shipment(seller_order_id, shop_id);
CREATE INDEX ix_shipment_warehouse_shop ON fulfillment.shipment(warehouse_id, shop_id);
CREATE TABLE fulfillment.shipment_line (
    shipment_id UUID NOT NULL,
    seller_order_id UUID NOT NULL,
    order_line_id UUID NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (shipment_id, order_line_id),
    FOREIGN KEY (shipment_id, seller_order_id) REFERENCES fulfillment.shipment(id, seller_order_id),
    FOREIGN KEY (order_line_id, seller_order_id) REFERENCES sales.order_line(id, seller_order_id)
);
CREATE INDEX ix_shipment_line_order ON fulfillment.shipment_line(order_line_id, seller_order_id);
CREATE TABLE fulfillment.shipment_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_id UUID NOT NULL REFERENCES fulfillment.shipment(id),
    provider_event_id VARCHAR(150) NOT NULL,
    raw_status VARCHAR(80) NOT NULL,
    normalized_status VARCHAR(30) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    redacted_payload JSONB NOT NULL,
    UNIQUE (shipment_id, provider_event_id)
);
CREATE TABLE fulfillment.cod_receivable (
    shipment_id UUID PRIMARY KEY REFERENCES fulfillment.shipment(id),
    expected_amount BIGINT NOT NULL CHECK (expected_amount >= 0),
    collected_amount BIGINT NOT NULL DEFAULT 0 CHECK (collected_amount >= 0),
    remitted_amount BIGINT NOT NULL DEFAULT 0 CHECK (remitted_amount >= 0),
    reconciled_at TIMESTAMPTZ,
    CHECK (remitted_amount <= collected_amount)
);

CREATE TABLE aftersales.return_case (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_order_id UUID NOT NULL REFERENCES sales.seller_order(id),
    buyer_account_id UUID NOT NULL REFERENCES identity.account(id),
    status VARCHAR(30) NOT NULL CHECK (status IN ('REQUESTED', 'REVIEWING', 'RETURN_REQUIRED', 'AWAITING_RETURN', 'IN_RETURN', 'INSPECTING', 'REFUND_APPROVED', 'REJECTED', 'DISPUTED', 'CLOSED', 'CANCELED')),
    resolution VARCHAR(30) CHECK (resolution IN ('REFUND_ONLY', 'RETURN_AND_REFUND', 'REJECT')),
    reason_code VARCHAR(50) NOT NULL,
    policy_id UUID NOT NULL REFERENCES platform.policy_version(id),
    return_shipment_id UUID REFERENCES fulfillment.shipment(id),
    deadline_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (id, seller_order_id)
);
CREATE INDEX ix_case_order ON aftersales.return_case(seller_order_id);
CREATE INDEX ix_case_buyer ON aftersales.return_case(buyer_account_id, created_at DESC);
CREATE INDEX ix_case_policy ON aftersales.return_case(policy_id);
CREATE INDEX ix_case_shipment ON aftersales.return_case(return_shipment_id);
CREATE TABLE aftersales.return_line (
    return_case_id UUID NOT NULL,
    seller_order_id UUID NOT NULL,
    order_line_id UUID NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (return_case_id, order_line_id),
    FOREIGN KEY (return_case_id, seller_order_id) REFERENCES aftersales.return_case(id, seller_order_id),
    FOREIGN KEY (order_line_id, seller_order_id) REFERENCES sales.order_line(id, seller_order_id)
);
CREATE INDEX ix_return_line_order ON aftersales.return_line(order_line_id, seller_order_id);
CREATE TABLE aftersales.case_evidence (
    return_case_id UUID NOT NULL REFERENCES aftersales.return_case(id),
    asset_id UUID NOT NULL REFERENCES media.asset(id),
    submitted_by UUID NOT NULL REFERENCES identity.account(id),
    PRIMARY KEY (return_case_id, asset_id)
);
CREATE INDEX ix_evidence_asset ON aftersales.case_evidence(asset_id);
CREATE INDEX ix_evidence_submitter ON aftersales.case_evidence(submitted_by);

CREATE TABLE payment.intent (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    checkout_group_id UUID NOT NULL REFERENCES sales.checkout_group(id),
    merchant_reference VARCHAR(100) NOT NULL UNIQUE,
    requested_amount BIGINT NOT NULL CHECK (requested_amount > 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    status VARCHAR(20) NOT NULL CHECK (status IN ('CREATED', 'PENDING', 'PAID', 'FAILED', 'UNKNOWN', 'CANCELED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (id, checkout_group_id)
);
CREATE INDEX ix_payment_intent_checkout ON payment.intent(checkout_group_id);
CREATE TABLE payment.attempt (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intent_id UUID NOT NULL REFERENCES payment.intent(id),
    checkout_group_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    merchant_attempt_reference VARCHAR(100) NOT NULL UNIQUE,
    provider_reference VARCHAR(150),
    status VARCHAR(20) NOT NULL CHECK (status IN ('CREATED', 'PENDING', 'SUCCEEDED', 'FAILED', 'EXPIRED', 'UNKNOWN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_reference),
    UNIQUE (id, checkout_group_id),
    FOREIGN KEY (intent_id, checkout_group_id) REFERENCES payment.intent(id, checkout_group_id)
);
CREATE INDEX ix_payment_attempt_intent ON payment.attempt(intent_id, created_at DESC);
CREATE TABLE payment.charge (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id UUID NOT NULL REFERENCES payment.attempt(id),
    checkout_group_id UUID NOT NULL,
    provider VARCHAR(50) NOT NULL,
    provider_charge_reference VARCHAR(150) NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    captured_at TIMESTAMPTZ NOT NULL,
    UNIQUE (provider, provider_charge_reference),
    UNIQUE (id, checkout_group_id),
    FOREIGN KEY (attempt_id, checkout_group_id) REFERENCES payment.attempt(id, checkout_group_id)
);
CREATE INDEX ix_payment_charge_attempt ON payment.charge(attempt_id);
CREATE TABLE payment.allocation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    charge_id UUID NOT NULL REFERENCES payment.charge(id),
    seller_order_id UUID NOT NULL REFERENCES sales.seller_order(id),
    checkout_group_id UUID NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    UNIQUE (charge_id, seller_order_id),
    UNIQUE (id, charge_id),
    FOREIGN KEY (charge_id, checkout_group_id) REFERENCES payment.charge(id, checkout_group_id),
    FOREIGN KEY (seller_order_id, checkout_group_id) REFERENCES sales.seller_order(id, checkout_group_id)
);
CREATE INDEX ix_payment_allocation_order ON payment.allocation(seller_order_id);
CREATE TABLE payment.refund (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    charge_id UUID NOT NULL REFERENCES payment.charge(id),
    return_case_id UUID REFERENCES aftersales.return_case(id),
    idempotency_key VARCHAR(150) NOT NULL UNIQUE,
    provider_refund_reference VARCHAR(150),
    amount BIGINT NOT NULL CHECK (amount > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('APPROVED', 'SUBMITTED', 'PENDING', 'SUCCEEDED', 'FAILED', 'UNKNOWN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (charge_id, provider_refund_reference),
    UNIQUE (id, charge_id)
);
CREATE INDEX ix_payment_refund_case ON payment.refund(return_case_id);
CREATE TABLE payment.refund_allocation (
    refund_id UUID NOT NULL,
    charge_id UUID NOT NULL,
    allocation_id UUID NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    PRIMARY KEY (refund_id, allocation_id),
    FOREIGN KEY (refund_id, charge_id) REFERENCES payment.refund(id, charge_id),
    FOREIGN KEY (allocation_id, charge_id) REFERENCES payment.allocation(id, charge_id)
);
CREATE INDEX ix_refund_allocation_charge ON payment.refund_allocation(allocation_id, charge_id);

CREATE TABLE finance.ledger_account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(80) NOT NULL,
    shop_id UUID REFERENCES merchant.shop(id),
    account_type VARCHAR(20) NOT NULL CHECK (account_type IN ('ASSET', 'LIABILITY', 'REVENUE', 'EXPENSE', 'EQUITY')),
    currency CHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    UNIQUE NULLS NOT DISTINCT (code, shop_id, currency),
    UNIQUE (id, currency)
);
CREATE INDEX ix_ledger_account_shop ON finance.ledger_account(shop_id);
CREATE TABLE finance.journal_entry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_event_key VARCHAR(150) NOT NULL UNIQUE,
    currency CHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    reversal_of UUID REFERENCES finance.journal_entry(id),
    created_transaction BIGINT NOT NULL DEFAULT txid_current(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (id, currency),
    CHECK (reversal_of IS NULL OR reversal_of <> id)
);
CREATE INDEX ix_journal_reversal ON finance.journal_entry(reversal_of);
CREATE TABLE finance.journal_line (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_id UUID NOT NULL,
    account_id UUID NOT NULL,
    currency CHAR(3) NOT NULL,
    debit BIGINT NOT NULL DEFAULT 0,
    credit BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (entry_id, currency) REFERENCES finance.journal_entry(id, currency),
    FOREIGN KEY (account_id, currency) REFERENCES finance.ledger_account(id, currency),
    CHECK ((debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0))
);
CREATE INDEX ix_journal_line_entry ON finance.journal_line(entry_id, currency);
CREATE INDEX ix_journal_line_account ON finance.journal_line(account_id, currency);
-- Aggregate balancing is enforced at transaction end, not with an invalid cross-row CHECK.
CREATE FUNCTION finance.check_balanced_entry() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    entry UUID;
    line_count BIGINT;
    difference NUMERIC;
BEGIN
    IF TG_TABLE_NAME = 'journal_entry' THEN entry := NEW.id;
    ELSE entry := NEW.entry_id;
    END IF;
    SELECT count(*), coalesce(sum(debit::numeric - credit::numeric), 0)
    INTO line_count, difference FROM finance.journal_line WHERE entry_id = entry;
    IF line_count < 2 OR difference <> 0 THEN
        RAISE EXCEPTION 'Journal entry must contain at least two balanced lines' USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;
CREATE CONSTRAINT TRIGGER journal_entry_balanced AFTER INSERT ON finance.journal_entry
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION finance.check_balanced_entry();
CREATE CONSTRAINT TRIGGER journal_line_balanced AFTER INSERT ON finance.journal_line
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION finance.check_balanced_entry();
CREATE FUNCTION finance.reject_journal_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Posted journals are immutable; append a reversal' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER journal_entry_immutable BEFORE UPDATE OR DELETE ON finance.journal_entry
    FOR EACH ROW EXECUTE FUNCTION finance.reject_journal_mutation();
CREATE TRIGGER journal_line_immutable BEFORE UPDATE OR DELETE ON finance.journal_line
    FOR EACH ROW EXECUTE FUNCTION finance.reject_journal_mutation();
CREATE FUNCTION finance.require_same_posting_transaction() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE posting_transaction BIGINT;
BEGIN
    SELECT created_transaction INTO posting_transaction FROM finance.journal_entry WHERE id = NEW.entry_id;
    IF posting_transaction <> txid_current() THEN
        RAISE EXCEPTION 'Cannot append lines to a previously committed journal' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER journal_line_same_transaction BEFORE INSERT ON finance.journal_line
    FOR EACH ROW EXECUTE FUNCTION finance.require_same_posting_transaction();

CREATE TABLE finance.settlement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id UUID NOT NULL REFERENCES merchant.shop(id),
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'RECONCILED', 'APPROVED', 'CLOSED')),
    UNIQUE (shop_id, period_start, period_end),
    UNIQUE (id, shop_id),
    CHECK (period_end > period_start)
);
CREATE TABLE finance.settlement_line (
    settlement_id UUID NOT NULL REFERENCES finance.settlement(id),
    seller_order_id UUID NOT NULL REFERENCES sales.seller_order(id),
    shop_id UUID NOT NULL,
    net_payable BIGINT NOT NULL,
    calculation_snapshot JSONB NOT NULL,
    PRIMARY KEY (settlement_id, seller_order_id),
    FOREIGN KEY (settlement_id, shop_id) REFERENCES finance.settlement(id, shop_id),
    FOREIGN KEY (seller_order_id, shop_id) REFERENCES sales.seller_order(id, shop_id)
);
CREATE INDEX ix_settlement_line_order ON finance.settlement_line(seller_order_id);
CREATE TABLE finance.payout (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shop_id UUID NOT NULL REFERENCES merchant.shop(id),
    bank_account_id UUID NOT NULL,
    idempotency_key VARCHAR(150) NOT NULL UNIQUE,
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    beneficiary_snapshot_ciphertext TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('REQUESTED', 'REVIEWING', 'APPROVED', 'SUBMITTED', 'SUCCEEDED', 'FAILED', 'UNKNOWN', 'REJECTED')),
    provider_reference VARCHAR(150) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (bank_account_id, shop_id) REFERENCES merchant.bank_account(id, shop_id)
);
CREATE INDEX ix_payout_shop_created ON finance.payout(shop_id, created_at DESC);
CREATE INDEX ix_payout_bank_shop ON finance.payout(bank_account_id, shop_id);
CREATE TABLE finance.reconciliation_batch (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(50) NOT NULL,
    source_checksum CHAR(64) NOT NULL,
    business_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, source_checksum)
);
CREATE TABLE finance.reconciliation_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id UUID NOT NULL REFERENCES finance.reconciliation_batch(id),
    provider_reference VARCHAR(150) NOT NULL,
    amount BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'VND' CHECK (currency = 'VND'),
    match_status VARCHAR(20) NOT NULL CHECK (match_status IN ('UNMATCHED', 'MATCHED', 'MISMATCH', 'REVIEWED')),
    journal_entry_id UUID REFERENCES finance.journal_entry(id),
    UNIQUE (batch_id, provider_reference)
);
CREATE INDEX ix_reconciliation_journal ON finance.reconciliation_item(journal_entry_id);

CREATE TABLE engagement.review (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_line_id UUID NOT NULL UNIQUE REFERENCES sales.order_line(id),
    buyer_account_id UUID NOT NULL REFERENCES identity.account(id),
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    content TEXT NOT NULL,
    status VARCHAR(10) NOT NULL CHECK (status IN ('VISIBLE', 'HIDDEN')),
    moderation_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status <> 'HIDDEN' OR moderation_reason IS NOT NULL)
);
CREATE INDEX ix_review_buyer ON engagement.review(buyer_account_id);
CREATE TABLE engagement.support_ticket (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    opened_by UUID NOT NULL REFERENCES identity.account(id),
    seller_order_id UUID REFERENCES sales.seller_order(id),
    assigned_to UUID REFERENCES identity.account(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    subject VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ticket_opener ON engagement.support_ticket(opened_by);
CREATE INDEX ix_ticket_order ON engagement.support_ticket(seller_order_id);
CREATE INDEX ix_ticket_assignee ON engagement.support_ticket(assigned_to, status);
CREATE TABLE engagement.ticket_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES engagement.support_ticket(id),
    author_account_id UUID NOT NULL REFERENCES identity.account(id),
    body TEXT NOT NULL,
    attachment_id UUID REFERENCES media.asset(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ticket_message_ticket ON engagement.ticket_message(ticket_id, created_at);
CREATE INDEX ix_ticket_message_author ON engagement.ticket_message(author_account_id);
CREATE INDEX ix_ticket_message_attachment ON engagement.ticket_message(attachment_id);

COMMIT;
