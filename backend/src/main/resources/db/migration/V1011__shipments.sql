-- S4 (docs/20): shipping through a carrier (GHTK first). Additive. A shipment belongs to one seller
-- order; every carrier event is stored once. The shop's pickup address is needed to create any
-- carrier order; phone and street are encrypted like other personal data.
CREATE SCHEMA fulfillment;

CREATE TABLE fulfillment.pickup_address (
    shop_id UUID PRIMARY KEY REFERENCES merchant.shop(id),
    contact_name VARCHAR(100) NOT NULL,
    phone_ciphertext TEXT NOT NULL,
    street_ciphertext TEXT NOT NULL,
    ward VARCHAR(100) NOT NULL,
    district VARCHAR(100) NOT NULL,
    province VARCHAR(100) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fulfillment.shipment (
    id UUID PRIMARY KEY,
    seller_order_id UUID NOT NULL,
    shop_id UUID NOT NULL,
    direction VARCHAR(10) NOT NULL DEFAULT 'FORWARD' CHECK (direction IN ('FORWARD', 'RETURN')),
    carrier_code VARCHAR(30) NOT NULL,
    partner_order_id VARCHAR(100) NOT NULL,
    tracking_number VARCHAR(100),
    status VARCHAR(30) NOT NULL CHECK (status IN ('CREATING', 'CREATED', 'FAILED', 'PICKED_UP', 'IN_TRANSIT', 'DELIVERED', 'DELIVERY_FAILED', 'RETURNING', 'RETURNED', 'CANCELED')),
    carrier_status_id INTEGER,
    cod_amount BIGINT NOT NULL DEFAULT 0 CHECK (cod_amount >= 0),
    weight_grams INTEGER NOT NULL CHECK (weight_grams > 0),
    declared_value BIGINT NOT NULL CHECK (declared_value > 0),
    carrier_fee BIGINT CHECK (carrier_fee >= 0),
    insurance_fee BIGINT CHECK (insurance_fee >= 0),
    failure_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_shipment_carrier_tracking UNIQUE (carrier_code, tracking_number),
    CONSTRAINT uq_shipment_carrier_partner UNIQUE (carrier_code, partner_order_id),
    CONSTRAINT uq_shipment_id_order UNIQUE (id, seller_order_id),
    CONSTRAINT fk_shipment_order_shop FOREIGN KEY (seller_order_id, shop_id)
        REFERENCES sales.seller_order(id, shop_id),
    CONSTRAINT ck_shipment_tracking CHECK (
        status IN ('CREATING', 'FAILED') OR tracking_number IS NOT NULL
    )
);
-- One live forward shipment per order; a failed or canceled one can be replaced.
CREATE UNIQUE INDEX uq_shipment_live_forward ON fulfillment.shipment(seller_order_id)
    WHERE direction = 'FORWARD' AND status NOT IN ('FAILED', 'CANCELED');
CREATE INDEX ix_shipment_order_shop ON fulfillment.shipment(seller_order_id, shop_id);

CREATE TABLE fulfillment.shipment_event (
    id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES fulfillment.shipment(id),
    provider_event_id VARCHAR(250) NOT NULL,
    raw_status VARCHAR(80) NOT NULL,
    normalized_status VARCHAR(30) NOT NULL,
    reason_code VARCHAR(20),
    reason VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_shipment_event UNIQUE (shipment_id, provider_event_id)
);
