-- D8: how long a shop has to confirm an order. A new policy type; the constraint is widened, no data moves.
ALTER TABLE platform.policy_version DROP CONSTRAINT ck_policy_type;
ALTER TABLE platform.policy_version ADD CONSTRAINT ck_policy_type CHECK (
    policy_type IN ('SHIPPING_FLAT', 'RETURN', 'COD_LIMITS', 'PAYMENT_WINDOW', 'SELLER_CONFIRMATION')
);
-- Support queue of overdue orders reads AWAITING_SELLER orders by age.
CREATE INDEX ix_order_awaiting ON sales.seller_order(created_at) WHERE status = 'AWAITING_SELLER';
