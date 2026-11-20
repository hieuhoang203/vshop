-- Buyer cart and price quote (backlog C-phase checkout foundation). Additive only: new schema and
-- tables, no existing data is moved or removed, so no data backup is required.
CREATE SCHEMA checkout;

-- A cart records purchase intent only; it never holds stock (BR03).
CREATE TABLE checkout.cart (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    buyer_account_id UUID NOT NULL REFERENCES identity.account(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cart_buyer UNIQUE (buyer_account_id),
    CONSTRAINT ck_cart_version CHECK (version >= 0)
);

CREATE TABLE checkout.cart_line (
    cart_id UUID NOT NULL REFERENCES checkout.cart(id),
    sku_id UUID NOT NULL REFERENCES catalog.product_sku(id),
    quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (cart_id, sku_id),
    CONSTRAINT ck_cart_line_quantity CHECK (quantity BETWEEN 1 AND 99)
);
CREATE INDEX ix_cart_line_sku ON checkout.cart_line(sku_id);

-- A quote is a server-calculated, expiring snapshot. cart_version ties it to the exact cart it was
-- calculated from, so any later cart change invalidates it (BR04). The price policy reference is
-- added with the policy module.
CREATE TABLE checkout.quote (
    id UUID PRIMARY KEY,
    buyer_account_id UUID NOT NULL REFERENCES identity.account(id),
    cart_id UUID NOT NULL REFERENCES checkout.cart(id),
    cart_version BIGINT NOT NULL,
    total_amount BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    snapshot JSONB NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_quote_cart_version CHECK (cart_version >= 0),
    CONSTRAINT ck_quote_total CHECK (total_amount >= 0),
    CONSTRAINT ck_quote_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_quote_snapshot CHECK (jsonb_typeof(snapshot) = 'object'),
    CONSTRAINT ck_quote_expiry CHECK (expires_at > created_at)
);
CREATE INDEX ix_quote_buyer ON checkout.quote(buyer_account_id, created_at DESC);
CREATE INDEX ix_quote_cart ON checkout.quote(cart_id);

COMMENT ON TABLE checkout.cart IS 'One cart per buyer account. Intent only; no stock hold. version increments on every line change.';
COMMENT ON TABLE checkout.quote IS 'Immutable calculated quote. Shipping, voucher and tax are not calculated yet and are absent from total_amount.';
