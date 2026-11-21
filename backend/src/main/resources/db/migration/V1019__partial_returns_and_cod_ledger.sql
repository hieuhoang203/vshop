-- R4 (docs/24): return cases can name the order lines (and quantities) being returned, and cash
-- collected on delivery is tracked until it reaches the platform. Additive only.
CREATE TABLE aftersales.return_case_item (
    return_case_id UUID NOT NULL REFERENCES aftersales.return_case(id),
    order_line_id UUID NOT NULL REFERENCES sales.order_line(id),
    quantity INTEGER NOT NULL CHECK (quantity > 0 AND quantity <= 1000),
    PRIMARY KEY (return_case_id, order_line_id)
);

-- Cash-on-delivery money of an order that was delivered. A carrier collects it and remits it later
-- (WITH_CARRIER until an administrator records the remittance); a shop that delivers by itself
-- keeps the cash, so that row is SETTLED from the start.
CREATE TABLE payment.cod_receivable (
    id UUID PRIMARY KEY,
    seller_order_id UUID NOT NULL UNIQUE,
    shop_id UUID NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    collected_by VARCHAR(10) NOT NULL CHECK (collected_by IN ('CARRIER', 'SHOP')),
    status VARCHAR(15) NOT NULL CHECK (status IN ('WITH_CARRIER', 'SETTLED')),
    settled_reference VARCHAR(100),
    settled_by_subject VARCHAR(255),
    settled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_cod_receivable_order FOREIGN KEY (seller_order_id, shop_id)
        REFERENCES sales.seller_order(id, shop_id),
    CONSTRAINT ck_cod_settled CHECK (
        status <> 'SETTLED' OR (settled_reference IS NOT NULL AND settled_at IS NOT NULL)
    )
);
CREATE INDEX ix_cod_receivable_open ON payment.cod_receivable(created_at) WHERE status = 'WITH_CARRIER';
