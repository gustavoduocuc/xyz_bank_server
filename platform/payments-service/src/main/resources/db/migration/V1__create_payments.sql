CREATE TABLE payments (
    id                     UUID PRIMARY KEY,
    type                   VARCHAR(16)    NOT NULL CHECK (type IN ('TRANSFER', 'DEPOSIT', 'BILL_PAYMENT')),
    source_account_id      UUID,
    destination_account_id UUID,
    amount                 NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency               VARCHAR(3)     NOT NULL,
    status                 VARCHAR(16)    NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'REJECTED')),
    idempotency_key        VARCHAR(64)    NOT NULL UNIQUE,
    created_at             TIMESTAMPTZ    NOT NULL,
    updated_at             TIMESTAMPTZ    NOT NULL,
    -- A transfer has both accounts, a deposit only a destination, a bill payment only a source
    CONSTRAINT payments_accounts_by_type CHECK (
        (type = 'TRANSFER' AND source_account_id IS NOT NULL AND destination_account_id IS NOT NULL
            AND source_account_id <> destination_account_id)
        OR (type = 'DEPOSIT' AND source_account_id IS NULL AND destination_account_id IS NOT NULL)
        OR (type = 'BILL_PAYMENT' AND source_account_id IS NOT NULL AND destination_account_id IS NULL))
);
