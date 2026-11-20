-- S1 of the checkout design (docs/20): orders created from a quote. Additive; no existing data moves.
-- sales.checkout_group, seller_order and order_line follow the reference design, plus the gaps found
-- while reviewing it: shop and product snapshots, readable ship region, a payment deadline,
-- cancel/confirm columns, the order-line <-> reservation link and a status history.
CREATE SCHEMA sales;

CREATE TABLE sales.checkout_group (
    id UUID PRIMARY KEY,
    buyer_account_id UUID NOT NULL REFERENCES identity.account(id),
    quote_id UUID NOT NULL REFERENCES checkout.quote(id),
    idempotency_key VARCHAR(150) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    total_amount BIGINT NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    policy_snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_group_quote UNIQUE (quote_id),
    CONSTRAINT uq_group_buyer_key UNIQUE (buyer_account_id, idempotency_key),
    CONSTRAINT ck_group_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_group_total CHECK (total_amount >= 0),
    CONSTRAINT ck_group_method CHECK (payment_method IN ('COD', 'PSP')),
    CONSTRAINT ck_group_policy CHECK (jsonb_typeof(policy_snapshot) = 'object')
);
CREATE INDEX ix_group_buyer ON sales.checkout_group(buyer_account_id, created_at DESC);

CREATE TABLE sales.seller_order (
    id UUID PRIMARY KEY,
    checkout_group_id UUID NOT NULL REFERENCES sales.checkout_group(id),
    shop_id UUID NOT NULL REFERENCES merchant.shop(id),
    order_number VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    item_amount BIGINT NOT NULL,
    discount_amount BIGINT NOT NULL,
    shipping_amount BIGINT NOT NULL,
    payable_amount BIGINT NOT NULL,
    shop_name_snapshot VARCHAR(100) NOT NULL,
    recipient_snapshot_ciphertext TEXT NOT NULL,
    ship_province VARCHAR(100) NOT NULL,
    ship_ward VARCHAR(100) NOT NULL,
    return_policy_id UUID NOT NULL REFERENCES platform.policy_version(id),
    payment_deadline TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    canceled_at TIMESTAMPTZ,
    cancel_reason VARCHAR(500),
    delivered_at TIMESTAMPTZ,
    return_eligible_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_order_number UNIQUE (order_number),
    CONSTRAINT uq_order_group_shop UNIQUE (checkout_group_id, shop_id),
    CONSTRAINT uq_order_id_shop UNIQUE (id, shop_id),
    CONSTRAINT ck_order_status CHECK (status IN ('PENDING_PAYMENT', 'AWAITING_SELLER', 'CONFIRMED', 'PACKING', 'READY_TO_SHIP', 'IN_FULFILLMENT', 'DELIVERED', 'COMPLETED', 'CANCEL_REQUESTED', 'CANCELED', 'REJECTED')),
    CONSTRAINT ck_order_version CHECK (version >= 0),
    CONSTRAINT ck_order_amounts CHECK (item_amount >= 0 AND discount_amount >= 0 AND shipping_amount >= 0 AND payable_amount >= 0),
    CONSTRAINT ck_order_discount CHECK (discount_amount <= item_amount),
    CONSTRAINT ck_order_payable CHECK (payable_amount = item_amount - discount_amount + shipping_amount),
    CONSTRAINT ck_order_cancel CHECK ((status = 'CANCELED') = (canceled_at IS NOT NULL))
);
CREATE INDEX ix_order_shop_created ON sales.seller_order(shop_id, created_at DESC);
CREATE INDEX ix_order_policy ON sales.seller_order(return_policy_id);
CREATE INDEX ix_order_deadline ON sales.seller_order(payment_deadline) WHERE status = 'PENDING_PAYMENT';

CREATE TABLE sales.order_line (
    id UUID PRIMARY KEY,
    seller_order_id UUID NOT NULL,
    shop_id UUID NOT NULL,
    sku_id UUID NOT NULL,
    product_id UUID NOT NULL REFERENCES catalog.product(id),
    product_slug VARCHAR(200) NOT NULL,
    product_name_snapshot VARCHAR(200) NOT NULL,
    image_key VARCHAR(200),
    seller_sku VARCHAR(80) NOT NULL,
    variant_snapshot JSONB NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price BIGINT NOT NULL,
    gross_amount BIGINT NOT NULL,
    discount_amount BIGINT NOT NULL DEFAULT 0,
    net_amount BIGINT NOT NULL,
    CONSTRAINT fk_order_line_order_shop FOREIGN KEY (seller_order_id, shop_id) REFERENCES sales.seller_order(id, shop_id),
    CONSTRAINT fk_order_line_sku_shop FOREIGN KEY (sku_id, shop_id) REFERENCES catalog.product_sku(id, shop_id),
    CONSTRAINT ck_order_line_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_line_price CHECK (unit_price > 0),
    CONSTRAINT ck_order_line_gross CHECK (gross_amount = unit_price * quantity),
    CONSTRAINT ck_order_line_discount CHECK (discount_amount >= 0),
    CONSTRAINT ck_order_line_net CHECK (net_amount = gross_amount - discount_amount),
    CONSTRAINT ck_order_line_variant CHECK (jsonb_typeof(variant_snapshot) = 'object')
);
CREATE INDEX ix_order_line_order_shop ON sales.order_line(seller_order_id, shop_id);
CREATE INDEX ix_order_line_sku_shop ON sales.order_line(sku_id, shop_id);
CREATE INDEX ix_order_line_product ON sales.order_line(product_id);

-- Which stock reservation backs each order line, so cancel, expiry and payment success can commit or
-- release exactly the stock of exactly this order.
CREATE TABLE sales.order_line_reservation (
    order_line_id UUID PRIMARY KEY REFERENCES sales.order_line(id),
    reservation_id UUID NOT NULL UNIQUE REFERENCES inventory.stock_reservation(id)
);

-- Append-only history of order status changes.
CREATE TABLE sales.order_status_history (
    id UUID PRIMARY KEY,
    seller_order_id UUID NOT NULL REFERENCES sales.seller_order(id),
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    actor_type VARCHAR(10) NOT NULL,
    actor_subject VARCHAR(255),
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_history_actor CHECK (actor_type IN ('BUYER', 'SELLER', 'ADMIN', 'SYSTEM'))
);
CREATE INDEX ix_history_order ON sales.order_status_history(seller_order_id, created_at, id);

COMMENT ON TABLE sales.seller_order IS 'One order per shop of a checkout group. recipient_snapshot_ciphertext is a PiiCipher envelope bound to the row id; ship_province/ship_ward are readable for zones.';
