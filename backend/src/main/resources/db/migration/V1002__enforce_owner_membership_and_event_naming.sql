ALTER TABLE merchant.shop
    ADD CONSTRAINT uq_shop_id_owner UNIQUE (id, owner_account_id);

-- Non-owner members have a NULL owner link; OWNER membership must match shop ownership.
ALTER TABLE merchant.shop_member
    ADD COLUMN owner_link_account_id UUID GENERATED ALWAYS AS (
        CASE WHEN role = 'OWNER' THEN account_id ELSE NULL END
    ) STORED,
    ADD CONSTRAINT fk_shop_member_owner_consistency
        FOREIGN KEY (shop_id, owner_link_account_id)
        REFERENCES merchant.shop(id, owner_account_id);
CREATE INDEX ix_shop_member_owner_link ON merchant.shop_member(shop_id, owner_link_account_id);

ALTER TABLE platform.platform_event_receipt RENAME TO event_receipt;
COMMENT ON COLUMN merchant.shop_member.owner_link_account_id IS
    'Generated FK key for OWNER consistency; not an additional authorization source.';
