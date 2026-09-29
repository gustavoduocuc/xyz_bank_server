-- 120-second windows opened by a verified card PIN (adopt-oauth2-tokens-between-services
-- design.md Decision 5): while active, ATM calls referencing one act for its customer.
CREATE TABLE atm_sessions (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    card_id UUID NOT NULL REFERENCES cards(id),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_atm_sessions_expires_at ON atm_sessions(expires_at);
