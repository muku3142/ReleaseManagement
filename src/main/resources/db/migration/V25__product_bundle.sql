-- A product bundle is a named group of tickets made on the Tickets page before any release exists;
-- adding it to a release creates a release bundle of the same name (V24).
CREATE TABLE product_bundle (
    id         UUID PRIMARY KEY,
    product_id UUID         NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT product_bundle_id_product_unique UNIQUE (id, product_id)
);

CREATE UNIQUE INDEX product_bundle_name_unique ON product_bundle (product_id, lower(name));

-- The ticket row carries product_id so that "a ticket is in one bundle per product" is a unique constraint.
CREATE TABLE product_bundle_ticket (
    bundle_id  UUID        NOT NULL,
    product_id UUID        NOT NULL,
    ticket_key VARCHAR(64) NOT NULL,
    PRIMARY KEY (bundle_id, ticket_key),
    CONSTRAINT product_bundle_ticket_bundle_fk FOREIGN KEY (bundle_id, product_id)
        REFERENCES product_bundle (id, product_id) ON DELETE CASCADE,
    CONSTRAINT product_bundle_ticket_one_bundle UNIQUE (product_id, ticket_key)
);
