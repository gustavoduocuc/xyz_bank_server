CREATE TABLE processed_interest_events (
    event_id VARCHAR(128) PRIMARY KEY,
    idempotency_key VARCHAR(64) NOT NULL
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_id VARCHAR(128) NOT NULL UNIQUE,
    event_type VARCHAR(64) NOT NULL,
    schema_version INT NOT NULL,
    account_id UUID NOT NULL REFERENCES accounts(id),
    period INT NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    interest_rate NUMERIC(6, 4) NOT NULL,
    opening_balance NUMERIC(19, 2) NOT NULL,
    closing_balance NUMERIC(19, 2) NOT NULL,
    occurred_on DATE NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE
);
