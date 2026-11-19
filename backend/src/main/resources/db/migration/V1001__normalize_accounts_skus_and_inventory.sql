-- V1/V2/V1000 are immutable historical migrations. Continue at V1001 so both
-- previously seeded local databases and empty production databases migrate forward.
CREATE SCHEMA identity;
CREATE SCHEMA inventory;
CREATE SCHEMA platform;
ALTER TABLE public.audit_event SET SCHEMA platform;
ALTER TABLE public.outbox_event SET SCHEMA platform;
ALTER TABLE public.platform_event_receipt SET SCHEMA platform;

CREATE TABLE identity.account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_subject VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_account_external_subject UNIQUE (external_subject),
    CONSTRAINT ck_account_subject_nonblank CHECK (length(btrim(external_subject)) > 0),
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ANONYMIZED'))
);

INSERT INTO identity.account (external_subject)
SELECT DISTINCT owner_subject FROM merchant.shop;

ALTER TABLE merchant.shop ADD COLUMN owner_account_id UUID;
UPDATE merchant.shop s SET owner_account_id = a.id
FROM identity.account a WHERE a.external_subject = s.owner_subject;
ALTER TABLE merchant.shop ALTER COLUMN owner_account_id SET NOT NULL;
ALTER TABLE merchant.shop
    ADD CONSTRAINT fk_shop_owner FOREIGN KEY (owner_account_id) REFERENCES identity.account(id),
    ADD CONSTRAINT uq_shop_owner_pilot UNIQUE (owner_account_id),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_shop_version CHECK (version >= 0),
    ADD CONSTRAINT ck_shop_rejection_reason CHECK (
        (status = 'REJECTED' AND length(btrim(rejection_reason)) >= 5)
        OR (status <> 'REJECTED' AND rejection_reason IS NULL)
    );
ALTER TABLE merchant.shop DROP COLUMN owner_subject;

CREATE TABLE merchant.shop_member (
    shop_id UUID NOT NULL REFERENCES merchant.shop(id),
    account_id UUID NOT NULL REFERENCES identity.account(id),
    role VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (shop_id, account_id),
    CONSTRAINT ck_shop_member_role CHECK (role IN ('OWNER', 'CATALOG_MANAGER', 'FULFILLMENT', 'SUPPORT', 'FINANCE'))
);
CREATE UNIQUE INDEX uq_shop_member_owner ON merchant.shop_member(shop_id) WHERE role = 'OWNER';
CREATE INDEX ix_shop_member_account ON merchant.shop_member(account_id, shop_id);
INSERT INTO merchant.shop_member(shop_id, account_id, role)
SELECT id, owner_account_id, 'OWNER' FROM merchant.shop;

CREATE TABLE merchant.shop_review (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL REFERENCES merchant.shop(id),
    reviewer_account_id UUID NOT NULL REFERENCES identity.account(id),
    decision VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_shop_review_decision CHECK (decision IN ('APPROVED', 'REJECTED')),
    CONSTRAINT ck_shop_review_reason CHECK (length(btrim(reason)) >= 5)
);
CREATE INDEX ix_shop_review_shop_created ON merchant.shop_review(shop_id, created_at DESC);
CREATE INDEX ix_shop_review_reviewer ON merchant.shop_review(reviewer_account_id);
-- Existing audit history is preserved; no invented reviews for historical decisions.

ALTER TABLE catalog.category
    ADD COLUMN parent_id UUID REFERENCES catalog.category(id),
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD CONSTRAINT ck_category_not_self_parent CHECK (parent_id IS NULL OR parent_id <> id),
    ADD CONSTRAINT ck_category_name CHECK (length(btrim(name)) > 0);
CREATE INDEX ix_category_parent ON catalog.category(parent_id);
ALTER TABLE catalog.product
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT uq_product_id_shop UNIQUE (id, shop_id),
    ADD CONSTRAINT ck_product_version CHECK (version >= 0),
    ADD CONSTRAINT ck_product_name CHECK (length(btrim(name)) > 0);
CREATE INDEX ix_product_shop ON catalog.product(shop_id, created_at DESC, id);
CREATE INDEX ix_product_category ON catalog.product(category_id);

CREATE TABLE catalog.product_sku (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    shop_id UUID NOT NULL,
    seller_sku VARCHAR(80) NOT NULL,
    variant_signature VARCHAR(255) NOT NULL DEFAULT 'DEFAULT',
    price BIGINT NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_sku_product_shop FOREIGN KEY (product_id, shop_id) REFERENCES catalog.product(id, shop_id),
    CONSTRAINT uq_sku_shop_code UNIQUE (shop_id, seller_sku),
    CONSTRAINT uq_sku_product_variant UNIQUE (product_id, variant_signature),
    CONSTRAINT uq_sku_id_shop UNIQUE (id, shop_id),
    CONSTRAINT uq_sku_id_product UNIQUE (id, product_id),
    CONSTRAINT ck_sku_price CHECK (price > 0),
    CONSTRAINT ck_sku_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_sku_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_sku_code CHECK (length(btrim(seller_sku)) > 0),
    CONSTRAINT ck_sku_signature CHECK (length(btrim(variant_signature)) > 0),
    CONSTRAINT ck_sku_version CHECK (version >= 0)
);
CREATE INDEX ix_sku_product_active ON catalog.product_sku(product_id, price, id) WHERE status = 'ACTIVE';
CREATE INDEX ix_sku_product_shop ON catalog.product_sku(product_id, shop_id);

CREATE TABLE catalog.product_option (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL REFERENCES catalog.product(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_product_option_code UNIQUE (product_id, code),
    CONSTRAINT uq_product_option_id_product UNIQUE (id, product_id),
    CONSTRAINT ck_product_option_code CHECK (length(btrim(code)) > 0),
    CONSTRAINT ck_product_option_name CHECK (length(btrim(name)) > 0)
);
CREATE TABLE catalog.option_value (
    id UUID PRIMARY KEY,
    option_id UUID NOT NULL REFERENCES catalog.product_option(id),
    code VARCHAR(50) NOT NULL,
    label VARCHAR(100) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_option_value_code UNIQUE (option_id, code),
    CONSTRAINT uq_option_value_id_option UNIQUE (id, option_id),
    CONSTRAINT ck_option_value_code CHECK (length(btrim(code)) > 0),
    CONSTRAINT ck_option_value_label CHECK (length(btrim(label)) > 0)
);
CREATE TABLE catalog.sku_option_value (
    sku_id UUID NOT NULL,
    product_id UUID NOT NULL,
    option_id UUID NOT NULL,
    option_value_id UUID NOT NULL,
    PRIMARY KEY (sku_id, option_id),
    CONSTRAINT fk_sku_option_sku_product FOREIGN KEY (sku_id, product_id) REFERENCES catalog.product_sku(id, product_id),
    CONSTRAINT fk_sku_option_product FOREIGN KEY (option_id, product_id) REFERENCES catalog.product_option(id, product_id),
    CONSTRAINT fk_sku_option_value FOREIGN KEY (option_value_id, option_id) REFERENCES catalog.option_value(id, option_id)
);
CREATE INDEX ix_sku_option_product ON catalog.sku_option_value(product_id);
CREATE INDEX ix_sku_option_option_product ON catalog.sku_option_value(option_id, product_id);
CREATE INDEX ix_sku_option_value_option ON catalog.sku_option_value(option_value_id, option_id);

CREATE TABLE inventory.warehouse (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL REFERENCES merchant.shop(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_warehouse_shop_code UNIQUE (shop_id, code),
    CONSTRAINT uq_warehouse_id_shop UNIQUE (id, shop_id),
    CONSTRAINT ck_warehouse_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_warehouse_code CHECK (length(btrim(code)) > 0),
    CONSTRAINT ck_warehouse_name CHECK (length(btrim(name)) > 0)
);
CREATE TABLE inventory.stock_balance (
    sku_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    shop_id UUID NOT NULL,
    on_hand BIGINT NOT NULL DEFAULT 0,
    reserved BIGINT NOT NULL DEFAULT 0,
    blocked BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (sku_id, warehouse_id),
    CONSTRAINT fk_stock_sku_shop FOREIGN KEY (sku_id, shop_id) REFERENCES catalog.product_sku(id, shop_id),
    CONSTRAINT fk_stock_warehouse_shop FOREIGN KEY (warehouse_id, shop_id) REFERENCES inventory.warehouse(id, shop_id),
    CONSTRAINT ck_stock_nonnegative CHECK (on_hand >= 0 AND reserved >= 0 AND blocked >= 0),
    CONSTRAINT ck_stock_available CHECK (reserved <= on_hand AND blocked <= on_hand - reserved),
    CONSTRAINT ck_stock_version CHECK (version >= 0)
);
CREATE INDEX ix_stock_sku_shop ON inventory.stock_balance(sku_id, shop_id);
CREATE INDEX ix_stock_warehouse_shop ON inventory.stock_balance(warehouse_id, shop_id);
CREATE INDEX ix_stock_shop ON inventory.stock_balance(shop_id);
CREATE TABLE inventory.stock_reservation (
    id UUID PRIMARY KEY,
    sku_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    quantity BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'HELD',
    reference_id UUID NOT NULL,
    idempotency_key VARCHAR(150) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_reservation_stock FOREIGN KEY (sku_id, warehouse_id) REFERENCES inventory.stock_balance(sku_id, warehouse_id),
    CONSTRAINT ck_reservation_quantity CHECK (quantity > 0),
    CONSTRAINT ck_reservation_status CHECK (status IN ('HELD', 'COMMITTED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT ck_reservation_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_reservation_key CHECK (length(btrim(idempotency_key)) > 0)
);
CREATE INDEX ix_reservation_expiry ON inventory.stock_reservation(expires_at, id) WHERE status = 'HELD';
CREATE INDEX ix_reservation_stock ON inventory.stock_reservation(sku_id, warehouse_id);
CREATE INDEX ix_reservation_reference ON inventory.stock_reservation(reference_id);
CREATE TABLE inventory.stock_movement (
    id UUID PRIMARY KEY,
    sku_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    movement_type VARCHAR(30) NOT NULL,
    delta_on_hand BIGINT NOT NULL DEFAULT 0,
    delta_reserved BIGINT NOT NULL DEFAULT 0,
    delta_blocked BIGINT NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(150) NOT NULL UNIQUE,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_movement_stock FOREIGN KEY (sku_id, warehouse_id) REFERENCES inventory.stock_balance(sku_id, warehouse_id),
    CONSTRAINT ck_movement_type CHECK (movement_type IN ('MIGRATION', 'RECEIPT', 'RESERVE', 'RELEASE', 'COMMIT', 'RETURN', 'ADJUSTMENT')),
    CONSTRAINT ck_movement_nonzero CHECK (delta_on_hand <> 0 OR delta_reserved <> 0 OR delta_blocked <> 0),
    CONSTRAINT ck_movement_reason CHECK (length(btrim(reason)) > 0),
    CONSTRAINT ck_movement_key CHECK (length(btrim(idempotency_key)) > 0)
);
CREATE INDEX ix_movement_stock_created ON inventory.stock_movement(sku_id, warehouse_id, created_at DESC);

-- Preserve product IDs and every original VND price / stock value.
INSERT INTO catalog.product_sku(id, product_id, shop_id, seller_sku, price, currency)
SELECT id, id, shop_id, 'LEGACY-' || id::text, price, currency FROM catalog.product;
INSERT INTO inventory.warehouse(id, shop_id, code, name)
SELECT gen_random_uuid(), shop_id, 'DEFAULT', 'Kho chuyển đổi dữ liệu'
FROM catalog.product GROUP BY shop_id;
INSERT INTO inventory.stock_balance(sku_id, warehouse_id, shop_id, on_hand)
SELECT p.id, w.id, p.shop_id, p.stock FROM catalog.product p
JOIN inventory.warehouse w ON w.shop_id = p.shop_id AND w.code = 'DEFAULT';
INSERT INTO inventory.stock_movement(id, sku_id, warehouse_id, movement_type, delta_on_hand, idempotency_key, reason)
SELECT gen_random_uuid(), b.sku_id, b.warehouse_id, 'MIGRATION', b.on_hand,
       'V1001:' || b.sku_id::text, 'Opening stock migrated from catalog.product'
FROM inventory.stock_balance b WHERE b.on_hand <> 0;
ALTER TABLE catalog.product DROP COLUMN price, DROP COLUMN currency, DROP COLUMN stock;

CREATE INDEX ix_audit_resource_created ON platform.audit_event(resource_id, created_at DESC);
CREATE INDEX ix_audit_actor_created ON platform.audit_event(actor_subject, created_at DESC);
CREATE INDEX ix_outbox_aggregate_created ON platform.outbox_event(aggregate_id, created_at);

COMMENT ON TABLE identity.account IS 'Domain identity only; credentials and authentication remain in Keycloak. Single trusted issuer in this release.';
COMMENT ON TABLE catalog.product IS 'Product content. Sellable unit price belongs to product_sku; warehouse quantities belong to inventory.stock_balance.';
COMMENT ON TABLE inventory.stock_balance IS 'Authoritative per SKU/warehouse quantities. Available = on_hand - reserved - blocked. Writes require service-level atomic transactions.';
COMMENT ON COLUMN inventory.stock_reservation.reference_id IS 'Checkout reference reserved for future checkout module; no checkout FK until that module is migrated.';
