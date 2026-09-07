CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    type VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    occurred_on DATE NOT NULL,
    description VARCHAR(500)
);

CREATE INDEX idx_transactions_account_id_occurred_on_id
    ON transactions(account_id, occurred_on DESC, id DESC);
