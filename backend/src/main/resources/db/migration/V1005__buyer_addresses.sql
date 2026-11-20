-- Buyer address book (backlog C01). Additive: a new table and nullable quote columns, so no data is
-- moved or removed and no backup is required.
--
-- Sensitive parts (phone, street/house detail) are stored only as AES-GCM envelopes produced by the
-- application (see PiiCipher); province and ward stay readable because delivery zones and
-- serviceability are decided from them. recipient_name is readable per the reference design.
CREATE TABLE identity.address (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES identity.account(id),
    label VARCHAR(30),
    recipient_name VARCHAR(100) NOT NULL,
    phone_ciphertext TEXT NOT NULL,
    street_ciphertext TEXT NOT NULL,
    ward VARCHAR(100) NOT NULL,
    province VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_address_recipient CHECK (length(btrim(recipient_name)) >= 2),
    CONSTRAINT ck_address_ward CHECK (length(btrim(ward)) >= 1),
    CONSTRAINT ck_address_province CHECK (length(btrim(province)) >= 1),
    CONSTRAINT ck_address_ciphertext CHECK (length(phone_ciphertext) > 0 AND length(street_ciphertext) > 0),
    CONSTRAINT ck_address_version CHECK (version >= 0)
);
CREATE INDEX ix_address_account ON identity.address(account_id, created_at, id);
-- At most one default address per account, enforced by the database.
CREATE UNIQUE INDEX uq_address_default ON identity.address(account_id) WHERE is_default;

-- A quote may be bound to the delivery address it was calculated for (BR04: changing the address
-- makes the quote stale). Only the region is copied; the quote never holds phone or street.
ALTER TABLE checkout.quote
    ADD COLUMN address_id UUID REFERENCES identity.address(id) ON DELETE SET NULL,
    ADD COLUMN address_version BIGINT,
    ADD COLUMN ship_province VARCHAR(100),
    ADD COLUMN ship_ward VARCHAR(100),
    ADD CONSTRAINT ck_quote_address_version CHECK (address_version IS NULL OR address_version >= 0);
CREATE INDEX ix_quote_address ON checkout.quote(address_id);

COMMENT ON TABLE identity.address IS 'Owner-only address book. phone_ciphertext and street_ciphertext are application-level AES-GCM envelopes bound to the row id.';
