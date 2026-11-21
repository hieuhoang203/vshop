-- Return parcels (buyer -> shop) use the same shipment table with direction = 'RETURN'.
-- At most one live return parcel per order; a failed or canceled one can be replaced.
CREATE UNIQUE INDEX uq_shipment_live_return ON fulfillment.shipment(seller_order_id)
    WHERE direction = 'RETURN' AND status NOT IN ('FAILED', 'CANCELED');

-- Refund reason for an order whose parcel could not be delivered and came back to the shop.
ALTER TABLE payment.refund DROP CONSTRAINT refund_reason_check;
ALTER TABLE payment.refund ADD CONSTRAINT refund_reason_check CHECK (
    reason IN ('ORDER_CANCELED', 'SELLER_REJECTED', 'PAID_AFTER_EXPIRY', 'ADMIN_GOODWILL',
               'RETURN_APPROVED', 'SHIPMENT_FAILED')
);
