ALTER TABLE accounts
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN daily_withdrawn_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN daily_withdrawn_date DATE;

ALTER TABLE transactions
    ADD COLUMN idempotency_key VARCHAR(64) UNIQUE;
