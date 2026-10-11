-- One row per consumed event: the event id is the idempotency key
CREATE TABLE notifications (
    event_id    VARCHAR(128)   PRIMARY KEY,
    customer_id VARCHAR(64)    NOT NULL,
    kind        VARCHAR(32)    NOT NULL,
    account_id  VARCHAR(64),
    type        VARCHAR(32),
    amount      NUMERIC(19, 2),
    currency    VARCHAR(3),
    occurred_at TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_notifications_customer_occurred ON notifications (customer_id, occurred_at DESC);
