-- Refresh-token metadata that Spring Authorization Server's own tables do not keep
-- (adopt-oauth2-tokens-between-services design.md Decision 3).

-- Mobile devices a customer has logged in from, and whether each was revoked for good.
CREATE TABLE device_registrations (
    device_id varchar(100) NOT NULL,
    customer_id varchar(36) NOT NULL,
    revoked boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (device_id)
);

-- Hashes of refresh tokens that were rotated out, and the login (authorization) they belong
-- to, so presenting one again can be recognised as reuse and revoke that login.
CREATE TABLE rotated_refresh_tokens (
    token_hash varchar(64) NOT NULL,
    authorization_id varchar(100) NOT NULL,
    rotated_at timestamp NOT NULL,
    PRIMARY KEY (token_hash)
);

CREATE INDEX idx_rotated_refresh_tokens_authorization_id ON rotated_refresh_tokens (authorization_id);
