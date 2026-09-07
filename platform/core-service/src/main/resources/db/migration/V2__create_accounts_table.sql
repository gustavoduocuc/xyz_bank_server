CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    account_number VARCHAR(20) NOT NULL UNIQUE,
    customer_id UUID NOT NULL REFERENCES customers(id),
    balance NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL
);

CREATE INDEX idx_accounts_customer_id ON accounts(customer_id);
