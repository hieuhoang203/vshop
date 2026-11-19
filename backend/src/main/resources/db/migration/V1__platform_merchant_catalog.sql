CREATE SCHEMA merchant;
CREATE SCHEMA catalog;

CREATE TABLE merchant.shop (
    id UUID PRIMARY KEY,
    owner_subject VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL CHECK (length(trim(name)) >= 3),
    description VARCHAR(2000) NOT NULL CHECK (length(trim(description)) >= 20),
    status VARCHAR(20) NOT NULL CHECK (status IN ('SUBMITTED','APPROVED','REJECTED')),
    rejection_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL)
);
CREATE INDEX shop_review_queue ON merchant.shop(created_at) WHERE status='SUBMITTED';

CREATE TABLE catalog.category (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    sort_order INTEGER NOT NULL DEFAULT 0
);
-- A single-SKU read model for the first slice; full variant/inventory commands are not exposed yet.
CREATE TABLE catalog.product (
    id UUID PRIMARY KEY,
    shop_id UUID NOT NULL REFERENCES merchant.shop(id),
    category_id UUID NOT NULL REFERENCES catalog.category(id),
    slug VARCHAR(160) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    price BIGINT NOT NULL CHECK (price > 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND' CHECK(currency='VND'),
    stock INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
    image_key VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK(status IN ('DRAFT','ACTIVE','HIDDEN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX product_active_category ON catalog.product(category_id,created_at DESC,id) WHERE status='ACTIVE';

CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    actor_subject VARCHAR(255) NOT NULL,
    action VARCHAR(100) NOT NULL,
    resource_id UUID NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ
);
CREATE INDEX outbox_pending ON outbox_event(created_at) WHERE processed_at IS NULL;
