-- Account lifecycle (add-account-lifecycle): status, alias, the account's own daily
-- withdrawal limit (NULL: the configured default applies, as before) and the idempotency
-- keys of the opening and of the last update or closure.
ALTER TABLE accounts
    ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN alias VARCHAR(60),
    ADD COLUMN daily_withdrawal_limit NUMERIC(19, 2),
    ADD COLUMN opening_idempotency_key VARCHAR(64) UNIQUE,
    ADD COLUMN last_command_idempotency_key VARCHAR(64);
