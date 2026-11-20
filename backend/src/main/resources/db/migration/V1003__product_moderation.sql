-- Product moderation (backlog C08, flow F02): sellers submit, admins approve or reject with a reason.
-- No column is moved or removed and existing statuses stay valid, so no data backup is required.
ALTER TABLE catalog.product DROP CONSTRAINT product_status_check;
ALTER TABLE catalog.product
    ADD COLUMN rejection_reason VARCHAR(500),
    ADD CONSTRAINT ck_product_status CHECK (
        status IN ('DRAFT', 'PENDING_REVIEW', 'REJECTED', 'ACTIVE', 'HIDDEN')
    ),
    ADD CONSTRAINT ck_product_rejection_reason CHECK (
        (status = 'REJECTED' AND length(btrim(rejection_reason)) >= 5)
        OR (status <> 'REJECTED' AND rejection_reason IS NULL)
    );
CREATE INDEX ix_product_review_queue ON catalog.product(created_at, id) WHERE status = 'PENDING_REVIEW';

CREATE TABLE catalog.product_review (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    shop_id UUID NOT NULL,
    reviewer_account_id UUID NOT NULL REFERENCES identity.account(id),
    decision VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    product_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_product_review_product_shop FOREIGN KEY (product_id, shop_id) REFERENCES catalog.product(id, shop_id),
    CONSTRAINT ck_product_review_decision CHECK (decision IN ('APPROVED', 'REJECTED')),
    CONSTRAINT ck_product_review_reason CHECK (length(btrim(reason)) >= 5),
    CONSTRAINT ck_product_review_version CHECK (product_version >= 0)
);
CREATE INDEX ix_product_review_product ON catalog.product_review(product_id, created_at DESC);
CREATE INDEX ix_product_review_reviewer ON catalog.product_review(reviewer_account_id);
CREATE INDEX ix_product_review_shop ON catalog.product_review(shop_id, product_id);

COMMENT ON TABLE catalog.product_review IS 'Immutable moderation decisions; product_version is the exact content version that was reviewed.';
