CREATE TABLE annual_interest_summaries (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    year INT NOT NULL,
    opening_balance NUMERIC(19, 2) NOT NULL,
    closing_balance NUMERIC(19, 2) NOT NULL,
    interest_rate NUMERIC(6, 4) NOT NULL,
    interest_amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    UNIQUE (account_id, year)
);
