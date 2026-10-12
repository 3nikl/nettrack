-- TMF622 Product Ordering: partners (resellers, enterprise customers) order telecom products.

CREATE TABLE product_orders (
    id                        VARCHAR(36)  PRIMARY KEY,      -- UUID, exposed as the TMF "id"
    external_id               VARCHAR(100) NOT NULL,         -- the PARTNER's own order number
    partner_id                VARCHAR(64)  NOT NULL,         -- relatedParty with role "Partner"
    description               TEXT,
    category                  VARCHAR(64),
    state                     VARCHAR(32)  NOT NULL,         -- acknowledged, inProgress, completed, ...
    order_date                TIMESTAMPTZ  NOT NULL,
    requested_completion_date TIMESTAMPTZ,
    completion_date           TIMESTAMPTZ,
    last_update               TIMESTAMPTZ  NOT NULL,
    version                   BIGINT       NOT NULL DEFAULT 0, -- optimistic locking; sent to clients as the ETag
    -- Safe retries: a partner re-sending the same order number can never create a second order
    CONSTRAINT uq_partner_external_id UNIQUE (partner_id, external_id)
);

CREATE INDEX idx_product_orders_state ON product_orders (state, order_date DESC);

CREATE TABLE product_order_items (
    pk                    BIGSERIAL    PRIMARY KEY,
    order_id              VARCHAR(36)  NOT NULL REFERENCES product_orders (id) ON DELETE CASCADE,
    item_id               VARCHAR(32)  NOT NULL,             -- TMF productOrderItem.id, e.g. "1"
    action                VARCHAR(16)  NOT NULL,             -- add / modify / delete / noChange
    product_offering_id   VARCHAR(64)  NOT NULL,             -- points into the TMF620 catalog
    product_offering_name VARCHAR(200),
    quantity              INT          NOT NULL DEFAULT 1,
    place_id              VARCHAR(64),                       -- service address (TMF674 geographic site)
    CONSTRAINT uq_order_item UNIQUE (order_id, item_id)
);

-- Transactional outbox: events are written in the SAME transaction as the order change,
-- then a relay publishes them (to Kafka in Part 5). No change is ever saved without its event.
CREATE TABLE order_events (
    id           BIGSERIAL   PRIMARY KEY,
    order_id     VARCHAR(36) NOT NULL,
    event_type   VARCHAR(64) NOT NULL,                       -- ProductOrderCreateEvent, ...StateChangeEvent, ...
    payload      TEXT        NOT NULL,                       -- JSON body of the event
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_order_events_unpublished ON order_events (id) WHERE published_at IS NULL;
