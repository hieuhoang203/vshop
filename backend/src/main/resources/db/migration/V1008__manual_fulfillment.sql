-- S2 (docs/20, D11): manual shipping mode. The seller types a tracking code and declares delivery;
-- nothing here is carrier-confirmed, so these columns must not drive payouts. Additive only.
ALTER TABLE sales.seller_order
    ADD COLUMN manual_tracking_code VARCHAR(100),
    ADD COLUMN shipped_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_order_tracking CHECK (
        manual_tracking_code IS NULL OR length(btrim(manual_tracking_code)) > 0
    ),
    ADD CONSTRAINT ck_order_shipped CHECK (
        status NOT IN ('IN_FULFILLMENT', 'DELIVERED', 'COMPLETED')
        OR (manual_tracking_code IS NOT NULL AND shipped_at IS NOT NULL)
    );
COMMENT ON COLUMN sales.seller_order.manual_tracking_code IS
    'Seller-entered tracking code (manual shipping mode). Not carrier-verified.';
